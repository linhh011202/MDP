// Core/Inc/pid.h

#ifndef INC_PID_H_
#define INC_PID_H_

#ifdef __cplusplus
extern "C" {
#endif

// This is the correct structure definition
typedef struct {
    // --- Gains (Tuning Parameters) ---
    float kp; // Proportional gain
    float ki; // Integral gain
    float kd; // Derivative gain

    // --- State Variables ---
    float setpoint;       // The target value we are trying to reach
    float integral_error; // The accumulated error over time (for the I-term)
    float last_measurement;   // The error from the previous computation (for the D-term)

    // --- Output Limits ---
    float output_min; // The minimum allowed output value
    float output_max; // The maximum allowed output value

} PID_Controller_t;


// --- Function Prototypes ---

/**
 * @brief Initializes a PID controller with its gains and limits.
 */
void PID_Init(PID_Controller_t *pid, float kp, float ki, float kd,
              float out_min, float out_max);

/**
 * @brief Computes the PID output based on the current value.
 * @note  This version includes an anti-windup clamp on the integral term.
 */
float PID_Compute(PID_Controller_t *pid, float setpoint, float current_value, float dt);

/**
 * @brief Resets the integral and derivative states of the controller.
 */
void PID_Reset(PID_Controller_t *pid);


#ifdef __cplusplus
}
#endif

#endif /* INC_PID_H_ */
