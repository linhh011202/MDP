// Core/Src/pid.c

#include "pid.h"

void PID_Init(PID_Controller_t *pid, float kp, float ki, float kd,
              float out_min, float out_max) {
    // Initialize gains
    pid->kp = kp;
    pid->ki = ki;
    pid->kd = kd;

    // Initialize output limits
    pid->output_min = out_min;
    pid->output_max = out_max;

    // Reset the controller's state
    PID_Reset(pid);
}

float PID_Compute(PID_Controller_t *pid, float setpoint, float current_value, float dt) {
    if (dt <= 0) {
        // Avoid division by zero or weird behavior
        return 0;
    }

    float error = setpoint - current_value;

    // Proportional term
    float p_out = pid->kp * error;

    // Derivative term (on measurement to prevent kick)
    float derivative = -(current_value - pid->last_measurement) / dt;
    float d_out = pid->kd * derivative;

    // Calculate preliminary output without the integral term
    float pre_output = p_out + d_out;

    // Calculate integral term
    float i_out = pid->ki * pid->integral_error;

    // Final output before clamping
    float output = pre_output + i_out;

    // --- Anti-windup and Output Clamping ---
    // If output is saturated, we might need to adjust the integral.
    // If not, we can update the integral normally.
    if (output > pid->output_max) {
        output = pid->output_max;
    } else if (output < pid->output_min) {
        output = pid->output_min;
    } else {
        // Only integrate if we are not saturated
        pid->integral_error += error * dt;
    }

    // Update state for next iteration
    pid->last_measurement = current_value;

    return output;
}

void PID_Reset(PID_Controller_t *pid) {
    pid->integral_error = 0.0f;
    pid->last_measurement = 0.0f; // Reset last measurement
}
