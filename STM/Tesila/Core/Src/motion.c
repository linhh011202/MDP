// motion.c (DROP-IN VERSION: sustained-deadband + settle window + corrected straight drive)

#include "motion.h"
#include "pid.h"
#include <math.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include "main.h"
#include "cmsis_os.h"

// ================== Shared/externs ==================
volatile int32_t last_servo_pulse = 0;
static volatile float drive_target_heading = 0.0f;
extern volatile float IR_Left_cm;
extern volatile float IR_Right_cm;
extern volatile float Front_Distance_cm;
extern volatile float initial_ultrasound_distance;


extern volatile int32_t encoder_count_A;
extern volatile int32_t encoder_count_D;
extern volatile float   yaw;
// ---- Steering trim config (tiny, simple) ----
// === Snappy steering trim (PD using gyro rate) ===
#define STEER_DB_ENTER_DEG     (0.6f)
#define STEER_DB_EXIT_DEG      (0.3f)
#define STEER_MAX_TRIM_DEG     (35)
#define STEER_KP_DEG           (1.6f)     // P on heading error (deg -> deg steering)
#define STEER_KD_DPS2DEG       (0.06f)    // D on yaw rate (deg/s -> deg steering)
#define GYRO_LPF_ALPHA         (0.4f)    // 0..1; higher = more responsive, less filtering
#define STEER_MIN_KICK_DEG     (6.0f)     // small “kick” to break static friction

static bool  steer_db_active = true;
static float z_dps_filt = 0.0f;          // filtered gyro rate

float IMU_GetYawLatest(void);
float IMU_GetGyroZLatest(void);



extern TIM_HandleTypeDef htim1;  // Motor D (Right) PWM timer
extern TIM_HandleTypeDef htim4;  // Motor A (Left)  PWM timer
extern TIM_HandleTypeDef htim3;  // Servo PWM timer

// ================== Private state ===================
static PID_Controller_t motor_pid_A;
static PID_Controller_t motor_pid_D;
static PID_Controller_t heading_pid;

static volatile bool     is_turning          = false;
static volatile float    turn_target_heading = 0.0f;
static volatile uint32_t turn_start_tick     = 0;

// Sustained deadband tracking
static volatile uint32_t turn_deadband_enter_tick = 0;
static const  uint32_t   TURN_HOLD_MS             = 400;

static volatile bool     is_arc_turn        = false;
static volatile float    arc_target_heading = 0.0f;
static volatile uint32_t arc_start_tick     = 0;
static volatile int32_t  arc_base_pwm       = 1700;
static volatile int      arc_steer_deg      = 45;

// Short settle period after any Motion_Stop()
static volatile uint32_t settle_until_tick  = 0;

// ================== Motor & Servo Configuration ====================
#define MOTOR_PWM_PERIOD 7199
#define MOTOR_A_POL   (+1)
#define MOTOR_D_POL   (-1)
#define ENCODER_A_POL (+1)
#define ENCODER_D_POL (-1)

#define SERVO_MIN_PULSE_US      500
#define SERVO_CENTER_PULSE_US  1500
#define SERVO_MAX_PULSE_US     2500
#define SERVO_TICK_US            10

// ================== Forward Declarations ======================
void Motion_Init(void);
void Motion_Set_Steering(int angle_deg);
void Motion_Move_Distance(float distance_cm);
void Motion_Turn_Angle(float angle_deg);
void Motion_Turn_Angle_Arc(float angle_deg, int steer_deg, int32_t pwm_forward);
void Motion_Turn_Angle_Arc_Backward(float angle_deg, int steer_deg, int32_t pwm_backward);
void Motion_Process(void);
bool Motion_Is_Busy(void);
void Motion_Stop(void);

// ================== Helpers =========================
static inline void set_motor_speed_raw(TIM_HandleTypeDef* htim,
                                       uint32_t channel1, uint32_t channel2,
                                       int32_t speed)
{
    if (speed > MOTOR_PWM_PERIOD)  speed = MOTOR_PWM_PERIOD;
    if (speed < -MOTOR_PWM_PERIOD) speed = -MOTOR_PWM_PERIOD;

    if (speed > 0) {
        __HAL_TIM_SET_COMPARE(htim, channel1, 0);
        __HAL_TIM_SET_COMPARE(htim, channel2, (uint32_t)speed);
    } else if (speed < 0) {
        __HAL_TIM_SET_COMPARE(htim, channel1, (uint32_t)(-speed));
        __HAL_TIM_SET_COMPARE(htim, channel2, 0);
    } else {
        __HAL_TIM_SET_COMPARE(htim, channel1, 0);
        __HAL_TIM_SET_COMPARE(htim, channel2, 0);
    }
}

static inline void set_left_speed(int32_t speed_left)
{
    set_motor_speed_raw(&htim4, TIM_CHANNEL_4, TIM_CHANNEL_3, MOTOR_A_POL * speed_left);
}
static inline void set_right_speed(int32_t speed_right)
{
    set_motor_speed_raw(&htim1, TIM_CHANNEL_4, TIM_CHANNEL_3, MOTOR_D_POL * speed_right);
}

static inline void set_forward_speed(int32_t speed_forward)
{
    set_left_speed(speed_forward);
    set_right_speed(speed_forward);
}

static inline float wrap180(float a)
{
    while (a <= -180.0f) a += 360.0f;
    while (a >   180.0f) a -= 360.0f;
    return a;
}

// ================== Public API ======================
void Motion_Init(void)
{
    const int32_t MAX_DRIVE_SPEED = 3300;

    PID_Init(&motor_pid_A, 10.0f, 0.0f, 0.0f, -MAX_DRIVE_SPEED, MAX_DRIVE_SPEED);
     PID_Init(&motor_pid_D, 10.0f, 0.0f, 0.0f, -MAX_DRIVE_SPEED, MAX_DRIVE_SPEED);
     PID_Init(&heading_pid, 13.0f, 0.0f, 3.0f, -1000, 1000);

    HAL_TIM_PWM_Start(&htim4, TIM_CHANNEL_3);
    HAL_TIM_PWM_Start(&htim4, TIM_CHANNEL_4);
    HAL_TIM_PWM_Start(&htim1, TIM_CHANNEL_3);
    HAL_TIM_PWM_Start(&htim1, TIM_CHANNEL_4);
    HAL_TIM_PWM_Start(&htim3, TIM_CHANNEL_4);

    Motion_Set_Steering(0);
}

void Motion_Set_Steering(int angle_deg)
{
    if (angle_deg > 90)  angle_deg = 90;
    if (angle_deg < -90) angle_deg = -90;

    const float us_per_deg = (float)(SERVO_MAX_PULSE_US - SERVO_MIN_PULSE_US) / 180.0f;
    int32_t pulse_us = (int32_t)((float)SERVO_CENTER_PULSE_US + angle_deg * us_per_deg);

    if (pulse_us < SERVO_MIN_PULSE_US) pulse_us = SERVO_MIN_PULSE_US;
    if (pulse_us > SERVO_MAX_PULSE_US) pulse_us = SERVO_MAX_PULSE_US;

    last_servo_pulse = pulse_us;

    uint32_t pulse_ticks = (uint32_t)(pulse_us / SERVO_TICK_US);
    __HAL_TIM_SET_COMPARE(&htim3, TIM_CHANNEL_4, pulse_ticks);
}

void Motion_Move_Distance(float distance_cm)
{
	Motion_Set_Steering(0);
    encoder_count_A = 0;
    encoder_count_D = 0;

    PID_Reset(&motor_pid_A);
    PID_Reset(&motor_pid_D);
    PID_Reset(&heading_pid);

    drive_target_heading = yaw;
    heading_pid.setpoint = drive_target_heading;

    int32_t target_counts = (int32_t)(distance_cm * (float)(COUNTS_PER_CM));
    motor_pid_A.setpoint = target_counts;
    motor_pid_D.setpoint = target_counts;

    is_turning  = false;
    is_arc_turn = false;
    turn_deadband_enter_tick = 0;
}

void Motion_Process(void)
{
	static uint32_t last_tick = 0;

	if (last_tick == 0) {

	last_tick = HAL_GetTick();

	}

	uint32_t current_tick = HAL_GetTick();

	float dt = (current_tick - last_tick) / 1000.0f; // dt in seconds

	last_tick = current_tick;
	// ---- Straight driving branch ----
	if (motor_pid_A.setpoint != 0) {

	    float a_counts = (float)(ENCODER_A_POL * encoder_count_A);
	    float d_counts = (float)(ENCODER_D_POL * encoder_count_D);

	    int32_t base_A = (int32_t)PID_Compute(&motor_pid_A, motor_pid_A.setpoint, a_counts, dt);
	    int32_t base_D = (int32_t)PID_Compute(&motor_pid_D, motor_pid_D.setpoint, d_counts, dt);

	    // Wheel mixing correction from your existing heading PID
	    int32_t heading_correction = (int32_t)PID_Compute(&heading_pid, heading_pid.setpoint, yaw, dt);

	    // Detect reverse by average base command
	    int32_t avg_base = (base_A + base_D);
	    if (avg_base < 0) {
	        heading_correction = -heading_correction; // invert when reversing
	    }

	    // ===== Snappy servo trim: PD on heading error with gyro rate =====
	    float target_yaw = heading_pid.setpoint;                 // set in Motion_Move_Distance()
	    float err_deg    = wrap180(target_yaw - yaw);
	    float abs_err    = fabsf(err_deg);

	    // Deadband with hysteresis
	    if (steer_db_active) {
	        if (abs_err > STEER_DB_ENTER_DEG) steer_db_active = false;
	    } else {
	        if (abs_err < STEER_DB_EXIT_DEG)  steer_db_active = true;
	    }

	    int trim_deg = 0;
	    if (!steer_db_active) {
	        // Read gyro rate (deg/s) and lightly low-pass it
	        float z_dps_raw = IMU_GetGyroZLatest();                 // +ve = yaw increasing
	        z_dps_filt += GYRO_LPF_ALPHA * (z_dps_raw - z_dps_filt);

	        // PD + “kick” (kick helps break static friction when trim is tiny)
	        float trim = (STEER_KP_DEG * err_deg) + (STEER_KD_DPS2DEG * (-z_dps_filt));
	        if (fabsf(trim) < STEER_MIN_KICK_DEG) {
	            trim = (err_deg >= 0.0f) ? +STEER_MIN_KICK_DEG : -STEER_MIN_KICK_DEG;
	        }

	        // Invert servo effect when reversing
	        if (avg_base < 0) trim = -trim;

	        // Clamp
	        if (trim >  STEER_MAX_TRIM_DEG) trim =  STEER_MAX_TRIM_DEG;
	        if (trim < -STEER_MAX_TRIM_DEG) trim = -STEER_MAX_TRIM_DEG;

	        trim_deg = (int)trim;
	    }

	    // Apply servo trim (center if inside deadband)
	    Motion_Set_Steering(trim_deg);

	    // Wheel speed mixing
	    int32_t speed_A = base_A + heading_correction;
	    int32_t speed_D = base_D - heading_correction;

	    set_left_speed(speed_A);
	    set_right_speed(speed_D);

	    // Completion check
	    float err_A = (float)motor_pid_A.setpoint - a_counts;
	    float err_D = (float)motor_pid_D.setpoint - d_counts;
	    if (fabsf(err_A) < 100.0f && fabsf(err_D) < 100.0f) {
	        Motion_Stop();
	    }
	}


    // ---- In-place turning branch ----
	// ---- In-place turning branch ----
	    else if (is_turning) {
	        if ((HAL_GetTick() - turn_start_tick) > 3000U) {
	            Motion_Stop();
	            Motion_Set_Steering(0);
	            turn_deadband_enter_tick = 0;
	            return;
	        }

	        float err     = wrap180(turn_target_heading - yaw);
	        float abs_err = fabsf(err);

	        // FIX #1: TIGHTEN DEADBAND FOR BETTER ACCURACY
	        const int32_t MIN_TURN_PWM   = 2000;
	        const int32_t MAX_TURN_PWM   = 3000;
	        const float   SLOWDOWN_ANGLE = 55.0f;
	        const float   STOP_DEADBAND  = 1.0f; // Reduced from 3.0f

	        // FIX #2: IMPROVED LOGIC TO STOP IMMEDIATELY AND PREVENT COASTING
	        if (abs_err < STOP_DEADBAND) {
	            // We've entered the target zone.
	            if (turn_deadband_enter_tick == 0) {
	                // First time entering: stop motors immediately to kill momentum.
	                set_left_speed(0);
	                set_right_speed(0);
	                turn_deadband_enter_tick = HAL_GetTick();
	            }

	            // If we have successfully stayed in the deadband for the hold time, the turn is complete.
	            if ((HAL_GetTick() - turn_deadband_enter_tick) >= TURN_HOLD_MS) {
	                Motion_Stop(); // Resets flags etc.
	                Motion_Set_Steering(0);
	                turn_deadband_enter_tick = 0;
	            }
	        } else {
	            // We are outside the target zone, so apply power.
	            int32_t turn_speed;
	            if (abs_err > SLOWDOWN_ANGLE) {
	                turn_speed = MAX_TURN_PWM;
	            } else {
	                float scale = abs_err / SLOWDOWN_ANGLE;
	                turn_speed = (int32_t)(MIN_TURN_PWM + scale * (MAX_TURN_PWM - MIN_TURN_PWM));
	            }

	            int dir = (err > 0.0f) ? +1 : -1;
	            set_left_speed(  dir * turn_speed);
	            set_right_speed(- dir * turn_speed);

	            // Reset the timer since we've left the deadband.
	            turn_deadband_enter_tick = 0;
	        }
	    }
    // ---- Arc turning branch ----
    else if (is_arc_turn) {
        float err = wrap180(arc_target_heading - yaw);

        if ((HAL_GetTick() - arc_start_tick) > 5000U) {
            Motion_Stop();
            Motion_Set_Steering(0);
            return;
        }

        const int32_t MIN_FWD_ABS = 1000;
        const int32_t MAX_FWD_ABS = abs(arc_base_pwm);
        float mag   = fabsf(err);
        float scale = (mag >= 15.0f) ? 1.0f
                      : (mag <= 2.0f ? 0.0f : (mag - 2.0f) / (15.0f - 2.0f));
        int32_t fwd_pwm_abs = (int32_t)(MIN_FWD_ABS + scale * (MAX_FWD_ABS - MIN_FWD_ABS));

        int32_t fwd_pwm = (arc_base_pwm > 0) ? fwd_pwm_abs : -fwd_pwm_abs;

        Motion_Set_Steering(arc_steer_deg);
        set_forward_speed(fwd_pwm);

        if (fabsf(err) < 2.0f) {
            Motion_Stop();
            Motion_Set_Steering(0);
        }
    }
}

bool Motion_Is_Busy(void)
{
    if ((int32_t)(HAL_GetTick() - settle_until_tick) < 0) {
        return true;
    }
    return (motor_pid_A.setpoint != 0) || is_turning || is_arc_turn;
}

void Motion_Turn_Angle(float angle_deg)
{
    angle_deg = wrap180(angle_deg);

    if (fabsf(angle_deg) < 0.5f) {
        Motion_Set_Steering(0);
        is_turning  = false;
        is_arc_turn = false;
        turn_deadband_enter_tick = 0;
        return;
    }

    if (angle_deg > 0.0f) {
        Motion_Set_Steering(-45);
    } else {
        Motion_Set_Steering(45);
    }
    osDelay(500);
    turn_target_heading = wrap180(yaw + angle_deg);


    turn_start_tick     = HAL_GetTick();

    motor_pid_A.setpoint = 0;
    motor_pid_D.setpoint = 0;
    is_arc_turn          = false;
    is_turning           = true;
    turn_deadband_enter_tick = 0;
}

void Motion_Turn_Angle_Arc(float angle_deg, int steer_deg, int32_t pwm_forward)
{
    angle_deg = wrap180(angle_deg);

    if (fabsf(angle_deg) < 0.5f) {
        Motion_Set_Steering(0);
        is_arc_turn = false;
        return;
    }

    int steer = (angle_deg > 0.0f) ? -abs(steer_deg) : +abs(steer_deg);
    if (steer >  90) steer =  90;
    if (steer < -90) steer = -90;

    arc_steer_deg       = -steer;
    arc_base_pwm        = abs((pwm_forward != 0) ? pwm_forward : 1700);
    arc_target_heading  = wrap180(yaw + angle_deg);
    arc_start_tick      = HAL_GetTick();

    motor_pid_A.setpoint = 0;
    motor_pid_D.setpoint = 0;
    is_turning           = false;

    Motion_Set_Steering(arc_steer_deg);
    is_arc_turn = true;
}

void Motion_Turn_Angle_Arc_Backward(float angle_deg, int steer_deg, int32_t pwm_backward)
{
    angle_deg = wrap180(angle_deg);

    if (fabsf(angle_deg) < 0.5f) {
        Motion_Set_Steering(0);
        is_arc_turn = false;
        return;
    }

    int steer = (angle_deg > 0.0f) ? -abs(steer_deg) : +abs(steer_deg);
    if (steer >  90) steer =  90;
    if (steer < -90) steer = -90;

    arc_steer_deg       = steer;
    arc_base_pwm        = -abs((pwm_backward != 0) ? pwm_backward : 1700);
    arc_target_heading  = wrap180(yaw + angle_deg);
    arc_start_tick      = HAL_GetTick();

    motor_pid_A.setpoint = 0;
    motor_pid_D.setpoint = 0;
    is_turning           = false;

    Motion_Set_Steering(arc_steer_deg);
    is_arc_turn = true;
}

void Motion_Stop(void)
{
    PID_Reset(&motor_pid_A);
    PID_Reset(&motor_pid_D);
    PID_Reset(&heading_pid);

    motor_pid_A.setpoint = 0;
    motor_pid_D.setpoint = 0;

    set_left_speed(0);
    set_right_speed(0);

    is_turning  = false;
    is_arc_turn = false;

    settle_until_tick = HAL_GetTick() + 150;
}

///// THE CODE HERE IS FOR TASK 2

// Sensor Control
  const float OB1_APPROACH_DISTANCE_CM = 30.0f;
  const float OB2_APPROACH_DISTANCE_CM = 35.0f;
  const float PARK_DISTANCE_CM = 20.0f;
  const float IR_OBSTACLE_THRESH_CM = 45.0f;
  const float FORWARD_TRAVEL_MAX_CM = 500.0f;

  // Safety constants after the first S-curve
  const float ULTRASOUND_SAFETY_STOP_CM = 22.0f;
  const float ULTRASOUND_REVERSE_TARGET_CM = 35.0f;
  const float REVERSE_TRAVEL_MAX_CM = -25.0f;

  // S-Curve Parameters (for Obstacle 1)
  const float ARC_HEADING_CHANGE_DEG = 45.0f;
  const int   ARC_STEER_ANGLE_DEG = 60;
  const int32_t ARC_PWM_SPEED = 2100;

  // U-Turn Maneuver Parameters (for Obstacle 2)
  const float TURN_90_DEG = 90.0f;
  const float UTURN_ARC_HEADING_DEG = 179.9f;
  const int   UTURN_ARC_STEER_DEG = 60;
  const int32_t UTURN_ARC_PWM_SPEED = 2100;
  const int   NINETY_DEG_ARC_STEER_DEG = 60;
  const int32_t NINETY_DEG_ARC_PWM_SPEED = 2100;

  // General Timing
  const int SETTLE_TIME_MS = 100;

void Motion_Sonny_Move_To_O1(void)
{
	 // === PART 1: Approach & Bypass Obstacle 1 (Forward Trip) ===
	       Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM);
	       while (Motion_Is_Busy() && (Front_Distance_cm > OB1_APPROACH_DISTANCE_CM || Front_Distance_cm < 0.0f))
	       {
	         Motion_Process(); osDelay(20);
	       }
	       Motion_Stop(); osDelay(SETTLE_TIME_MS);

}

void Motion_Sonny_Dodge_O1_Left(void){
	       // S-Curve to pass on the left
	       Motion_Turn_Angle_Arc(-ARC_HEADING_CHANGE_DEG, ARC_STEER_ANGLE_DEG, ARC_PWM_SPEED);
	       while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	       osDelay(SETTLE_TIME_MS);
	       while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	       osDelay(SETTLE_TIME_MS);
	       Motion_Turn_Angle_Arc(ARC_HEADING_CHANGE_DEG * 2.0f, ARC_STEER_ANGLE_DEG, ARC_PWM_SPEED);
	       while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	       osDelay(SETTLE_TIME_MS);
	       Motion_Turn_Angle_Arc(-ARC_HEADING_CHANGE_DEG, ARC_STEER_ANGLE_DEG, ARC_PWM_SPEED);
	       while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	       osDelay(SETTLE_TIME_MS);

	       Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM);
	      	      	      while (Motion_Is_Busy() && (Front_Distance_cm > OB2_APPROACH_DISTANCE_CM || Front_Distance_cm < 0.0f))
	      	      	      {
	      	      	        Motion_Process(); osDelay(20);
	      	      	      }
	      	      	      Motion_Stop(); osDelay(SETTLE_TIME_MS);

	       // Safety Reverse Logic After Obstacle 1
	       if (Front_Distance_cm < ULTRASOUND_SAFETY_STOP_CM && Front_Distance_cm > 0.0f)
	       {
	           Motion_Move_Distance(REVERSE_TRAVEL_MAX_CM); // Start reversing
	           while(Motion_Is_Busy() && (Front_Distance_cm < ULTRASOUND_REVERSE_TARGET_CM)) {
	               Motion_Process(); osDelay(20);
	           }
	           Motion_Stop(); // Stop the reverse motion
	           osDelay(SETTLE_TIME_MS);
	       }


}
void Motion_Sonny_Dodge_O1_Right(void){
	       // S-Curve to pass on the right
	       Motion_Turn_Angle_Arc(ARC_HEADING_CHANGE_DEG, ARC_STEER_ANGLE_DEG, ARC_PWM_SPEED);
	       while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	       osDelay(SETTLE_TIME_MS);

	       Motion_Turn_Angle_Arc(-ARC_HEADING_CHANGE_DEG * 2.0f, ARC_STEER_ANGLE_DEG, ARC_PWM_SPEED);
	       while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	       osDelay(SETTLE_TIME_MS);
	       Motion_Turn_Angle_Arc(ARC_HEADING_CHANGE_DEG, ARC_STEER_ANGLE_DEG, ARC_PWM_SPEED);
	       while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	       osDelay(SETTLE_TIME_MS);

	       Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM);

		  while (Motion_Is_Busy() && (Front_Distance_cm > OB2_APPROACH_DISTANCE_CM || Front_Distance_cm < 0.0f))
		  {
			Motion_Process(); osDelay(20);
		  }
		  Motion_Stop(); osDelay(SETTLE_TIME_MS);

	       // Safety Reverse Logic After Obstacle 1
	       if (Front_Distance_cm < ULTRASOUND_SAFETY_STOP_CM && Front_Distance_cm > 0.0f)
	       {
	           Motion_Move_Distance(REVERSE_TRAVEL_MAX_CM); // Start reversing
	           while(Motion_Is_Busy() && (Front_Distance_cm < ULTRASOUND_REVERSE_TARGET_CM)) {
	               Motion_Process(); osDelay(20);
	           }
	           Motion_Stop(); // Stop the reverse motion
	           osDelay(SETTLE_TIME_MS);
	       }


}

void Motion_Sonny_Dodge_O2_Left_And_Home(float initial_ultrasound_distance){
	// === PART 2: U-Turn Maneuver around Obstacle 2 ===

	      // (U-Turn logic is unchanged as it was correct)
	      Motion_Turn_Angle_Arc(-TURN_90_DEG, NINETY_DEG_ARC_STEER_DEG, NINETY_DEG_ARC_PWM_SPEED); //left turn
	      while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	      osDelay(SETTLE_TIME_MS);

	      Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM); //max forward
	      while (Motion_Is_Busy() && (IR_Right_cm < IR_OBSTACLE_THRESH_CM && IR_Right_cm > 0.0f)) { Motion_Process(); osDelay(20); }
	      Motion_Stop(); osDelay(SETTLE_TIME_MS);
	      Motion_Turn_Angle_Arc(UTURN_ARC_HEADING_DEG, UTURN_ARC_STEER_DEG, UTURN_ARC_PWM_SPEED); //uturn
	      while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	      osDelay(SETTLE_TIME_MS);
	      Motion_Move_Distance(20.0f); // forward 20 for short ob 2
	        while (Motion_Is_Busy())
	        {
	          Motion_Process(); osDelay(20);
	        }
	      Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM); //max foward
	      while (Motion_Is_Busy() && (IR_Right_cm < IR_OBSTACLE_THRESH_CM && IR_Right_cm > 0.0f)) { Motion_Process(); osDelay(20); }
	      Motion_Stop(); osDelay(SETTLE_TIME_MS);
	      Motion_Turn_Angle_Arc(TURN_90_DEG, NINETY_DEG_ARC_STEER_DEG, NINETY_DEG_ARC_PWM_SPEED); //90 turn
	      while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	      osDelay(SETTLE_TIME_MS);

	      // === PART 3: Approach & Bypass Obstacle 1 (Return Trip) ===

	      Motion_Move_Distance(30.0f);//avoid ob 2
	      while(Motion_Is_Busy()){
	    	  Motion_Process(); osDelay(20);
	      }
	      Motion_Stop();
	      osDelay(SETTLE_TIME_MS);

	      Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM); // till ob 1
	      // Keep moving forward until the RIGHT IR sensor detects the edge of Obstacle 1
	      while (Motion_Is_Busy() && (IR_Right_cm > 79.9f || IR_Right_cm < 0.0f))
	      {
	        Motion_Process(); osDelay(20);
	      }
	      Motion_Stop(); // Stop immediately when the obstacle is detected
	      Motion_Move_Distance(10.0f);
	      while(Motion_Is_Busy()){
	    	  Motion_Process(); osDelay(20);
	      }
	      Motion_Stop();
	      osDelay(SETTLE_TIME_MS);
	      Motion_Turn_Angle_Arc(TURN_90_DEG, NINETY_DEG_ARC_STEER_DEG, NINETY_DEG_ARC_PWM_SPEED);
	      while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	      osDelay(SETTLE_TIME_MS);
	      Motion_Move_Distance(-25.0f);
	    	      while(Motion_Is_Busy()){
	    	    	  Motion_Process(); osDelay(20);
	    	      }
	      Motion_Stop();
	      osDelay(SETTLE_TIME_MS);
	      Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM); //max forward
	      while (Motion_Is_Busy() && (IR_Right_cm > 30.0f || IR_Right_cm < 0.0f))
	      {
	        Motion_Process(); osDelay(20);
	      }
	      Motion_Stop();
	      Motion_Move_Distance(-20.0f);
	    	      while(Motion_Is_Busy()){
	    	    	  Motion_Process(); osDelay(20);
	    	      }
	      Motion_Turn_Angle_Arc(-90.0f, NINETY_DEG_ARC_STEER_DEG, NINETY_DEG_ARC_PWM_SPEED);
	      while(Motion_Is_Busy()){
	    	  Motion_Process(); osDelay(20);
	      }

	      Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM);

	             while (Motion_Is_Busy() && (Front_Distance_cm > PARK_DISTANCE_CM || Front_Distance_cm < 0.0f))
	                   {
	                    Motion_Process(); osDelay(20);
	                    }
	      Motion_Stop(); osDelay(SETTLE_TIME_MS);


}

void Motion_Sonny_Dodge_O2_Right_And_Home(float initial_ultrasound_distance){
	// === PART 2: U-Turn Maneuver around Obstacle 2 ===



	      // (U-Turn logic is unchanged as it was correct)
	      Motion_Turn_Angle_Arc(TURN_90_DEG, NINETY_DEG_ARC_STEER_DEG, NINETY_DEG_ARC_PWM_SPEED);
	      while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	      osDelay(SETTLE_TIME_MS);

	      Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM);
	      while (Motion_Is_Busy() && (IR_Left_cm < IR_OBSTACLE_THRESH_CM && IR_Left_cm > 0.0f)) { Motion_Process(); osDelay(20); }
	      Motion_Stop(); osDelay(SETTLE_TIME_MS);
	      Motion_Turn_Angle_Arc(-UTURN_ARC_HEADING_DEG, UTURN_ARC_STEER_DEG, UTURN_ARC_PWM_SPEED);
	      while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	      osDelay(SETTLE_TIME_MS);
	      Motion_Move_Distance(20.0f); // forward 20 for short ob 2
	        while (Motion_Is_Busy())
	        {
	          Motion_Process(); osDelay(20);
	        }
	      Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM);
	      while (Motion_Is_Busy() && (IR_Left_cm < IR_OBSTACLE_THRESH_CM && IR_Left_cm > 0.0f)) { Motion_Process(); osDelay(20); }
	      Motion_Stop(); osDelay(SETTLE_TIME_MS);
	      Motion_Turn_Angle_Arc(-TURN_90_DEG, NINETY_DEG_ARC_STEER_DEG, NINETY_DEG_ARC_PWM_SPEED);
	      while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	      osDelay(SETTLE_TIME_MS);

	      // === PART 3: Approach & Bypass Obstacle 1 (Return Trip) ===

	      Motion_Move_Distance(30.0f);//avoid ob 2
	      while(Motion_Is_Busy()){
	    	  Motion_Process(); osDelay(20);
	      }
	      Motion_Stop();
	      osDelay(SETTLE_TIME_MS);

	      Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM);
	      // Keep moving forward until the LEFT IR sensor detects the edge of Obstacle 1
	      while (Motion_Is_Busy() && (IR_Left_cm > 79.9f || IR_Left_cm < 0.0f))
	      {
	        Motion_Process(); osDelay(20);
	      }
	      Motion_Stop(); // Stop immediately when the obstacle is detected


	      Motion_Move_Distance(10.0f);
	      while(Motion_Is_Busy()){
	    	  Motion_Process(); osDelay(20);
	      }
	      Motion_Stop();
	      osDelay(SETTLE_TIME_MS);
	      Motion_Turn_Angle_Arc(-TURN_90_DEG, NINETY_DEG_ARC_STEER_DEG, NINETY_DEG_ARC_PWM_SPEED);
	      while (Motion_Is_Busy()) { Motion_Process(); osDelay(20); }
	      osDelay(SETTLE_TIME_MS);
	      Motion_Move_Distance(-25.0f);
	    	      while(Motion_Is_Busy()){
	    	    	  Motion_Process(); osDelay(20);
	    	      }
	    	      Motion_Stop();
	    	      osDelay(SETTLE_TIME_MS);
	      Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM);
	    	      // Keep moving forward until the LEFT IR sensor detects the edge of Obstacle 1
	    	      while (Motion_Is_Busy() && (IR_Left_cm > 30.0f || IR_Left_cm < 0.0f))
	    	      {
	    	        Motion_Process(); osDelay(20);
	    	      }
	    	      Motion_Stop();
	      Motion_Move_Distance(-20.0f);
	    	      while(Motion_Is_Busy()){
	    	    	  Motion_Process(); osDelay(20);
	    	      }
	      Motion_Turn_Angle_Arc(90.0f, NINETY_DEG_ARC_STEER_DEG, NINETY_DEG_ARC_PWM_SPEED);
	      while(Motion_Is_Busy()){
	    	  Motion_Process(); osDelay(20);
	      }
	      Motion_Move_Distance(FORWARD_TRAVEL_MAX_CM);

	             while (Motion_Is_Busy() && (Front_Distance_cm > PARK_DISTANCE_CM || Front_Distance_cm < 0.0f))
	                   {
	                    Motion_Process(); osDelay(20);
	                    }
	      Motion_Stop(); osDelay(SETTLE_TIME_MS);

}

////// THE CODE ENDS FOR TASK 2

