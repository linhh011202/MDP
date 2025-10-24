#ifndef INC_MOTION_H_
#define INC_MOTION_H_

#include "main.h"
#include <stdbool.h>

/* USER CODE BEGIN Private defines */

// This constant converts encoder counts to centimeters.
// You MUST calibrate this value for your specific robot.
#define COUNTS_PER_CM 73.2f //Example value, needs calibration

/* USER CODE END Private defines */

/**
 * @brief Initializes the motion control system, motors, servo, and PIDs.
 */

void Motion_Turn_Angle_Arc_Backward(float angle_deg, int steer_deg, int32_t pwm_backward);
void Motion_Init(void);

/**
 * @brief Sets the steering angle of the front servo.
 * @param angle_deg: The angle in degrees (-90 to +90).
 */
void Motion_Set_Steering(int angle_deg); // <-- ADDED

/**
 * @brief Commands the robot to move a specific distance. This is non-blocking.
 * @param distance_cm: The distance to move in centimeters. Can be negative.
 */
void Motion_Move_Distance(float distance_cm);

/**
 * @brief Commands the robot to turn a specific angle. This is non-blocking.
 * @param angle_deg: The relative angle to turn in degrees.
 */
void Motion_Turn_Angle(float angle_deg);

/**
 * @brief The main processing loop for motion control. Call this repeatedly.
 */
void Motion_Process(void); // <-- ADDED

/**
 * @brief Checks if a motion command (move or turn) is currently active.
 * @return True if the robot is busy, false otherwise.
 */
bool Motion_Is_Busy(void); // <-- ADDED

/**
 * @brief Immediately stops all motor movement and resets the motion state.
 */
void Motion_Stop(void);

void Motion_Turn_Angle_Arc(float angle_deg, int steer_deg, int32_t pwm_forward);

// Task 2 helpers
void Motion_Sonny_Move_To_O1(void);
void Motion_Sonny_Dodge_O1_Left(void);
void Motion_Sonny_Dodge_O1_Right(void);
void Motion_Sonny_Dodge_O2_Left_And_Home(float initial_ultrasound_distance);
void Motion_Sonny_Dodge_O2_Right_And_Home(float initial_ultrasound_distance);

#endif /* INC_MOTION_H_ */
