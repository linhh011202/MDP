/*
 * app_main.h
 *
 *  Created on: Jan 22, 2024
 *      Author: edward62740
 */

#ifndef INC_APP_MAIN_H_
#define INC_APP_MAIN_H_

#ifdef __cplusplus
extern "C" {
#endif
#include "cmsis_os2.h"
#include "ICM20948.h"

void sensorTask(void *pv);
void UARTReceiveTask(void const * argument);
void initializeCPPconstructs(void);
extern bool test_run;
typedef struct {
	ICM20948 *imu;
	float ir_distL;
	float ir_distR;
	float usonic_dist;
	float yaw_abs;
	float yaw_abs_prev; // just to determine sgn(cur-prev yaw)
	uint32_t yaw_abs_time;
	float yaw_cur_dbg;
	uint32_t d_valid; // bitwise data valid mask. starts with LSB.
	uint32_t ql; // length of uart incoming queue
	bool is_allow_motor_override;  // gives this application power to override uart commands to stop if ir_distL || ir_dist R < th
	float ir_dist_th_L; // threshold value for above member.
	float ir_dist_th_R; // threshold value for above member.
	uint32_t last_halt_val; // holds the value of the magnitude of the previous motion cmd executed if applicable
} sensorData_t;

typedef struct {
	bool proc;
	bool lsnr;
	bool senr;
	bool motn;
} isTaskAlive_t;

const uint32_t AC_VALID_MASK = 0x1;
const uint32_t GY_VALID_MASK = 0x2;
const uint32_t MG_VALID_MASK = 0x4;
const uint32_t TM_VALID_MASK = 0x8;
const uint32_t IR_L_VALID_MASK = 0x10;
const uint32_t IR_R_VALID_MASK = 0x20;
const uint32_t USONIC_VALID_MASK = 0x40;
extern sensorData_t sensor_data;
extern isTaskAlive_t is_task_alive_struct;
void _ext_sig_halt(void);
void processorTask(void const *);
void quaternionUpdate(float w_x, float w_y, float w_z, float a_x, float a_y, float a_z, float deltat);
#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* INC_APP_MAIN_H_ */
