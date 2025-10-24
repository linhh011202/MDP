/*
 * app_parser.h
 *
 * Created on: Oct 4, 2025
 * Author: Your Name
 */

#ifndef INC_APP_PARSER_H_
#define INC_APP_PARSER_H_

#include "cmsis_os.h"
#include <stdint.h>
#include <stdbool.h>
#define IS_BUSY_CHAR 'b'
// Forward declaration of the motion packet struct
struct MOTION_PKT;

typedef struct
{
	osThreadId_t runner; // task handle
	osThreadAttr_t attr;
	struct {
		osMessageQueueId_t queue;
	} mailbox;

} u_ctx ; // struct containing context info for a task.

typedef struct {
    uint8_t buffer[10];
    uint32_t length;
} AppMessage_t ; // struct containing dma rxbuf

typedef uint8_t BUF_CMP_t; // buf type


// In app_parser.h

typedef enum
{
    MOVE_FWD = 0,
    MOVE_BWD,
    MOVE_RIGHT_FWD,
    MOVE_LEFT_FWD,
    MOVE_RIGHT_BWD,  // <-- ADD THIS
    MOVE_LEFT_BWD,   // <-- ADD THIS
    MOVE_HALT,
    // You can remove the T2 commands if they are not used by the parser
    MOVE_T2_S180R,
    MOVE_T2_S90R,

    MOVE_T2_O1,

	MOVE_SONNY_MOVE_TO_O1,
	MOVE_SONNY_DODGE_O1_LEFT,
	MOVE_SONNY_DODGE_O1_RIGHT,
	MOVE_SONNY_DODGE_O2_LEFT_AND_HOME,
	MOVE_SONNY_DODGE_O2_RIGHT_AND_HOME
} MOTION_CMD;

typedef struct MOTION_PKT
{
	MOTION_CMD cmd;
	uint32_t arg;
	bool is_absol;
	bool turn_opt;
	bool is_crawl;
	bool linear;
} MOTION_PKT_t; // struct containing msg to send to motioncontroller queue


/* Function Prototypes for app_parser.c */

void Parser_Init(osMessageQueueId_t motor_queue);
void Parser_Process(void);


/* PUBLIC CONSTANTS FOR UART CMDS */

#define ACK_MSG   "ack"
#define NACK_MSG  "nack"
#define OBST_MSG  "obst"

//index 0
#define START_CHAR 'x'

// index 1
#define CMD_CHAR 'c'
#define REQ_CHAR 'q'

// index 2
#define MOTOR_CHAR 'm'
#define SENSOR_CHAR 's'
#define AUX_CHAR 'a'

// index 3 iff [2] is MOTOR_CHAR
#define FWD_CHAR 'f'
#define BWD_CHAR 'b'
#define RIGHT_CHAR 'r'
#define LEFT_CHAR 'l'
#define HALT_CHAR 'h'

#define CRAWL_CHAR 'd'
#define LINEAR_CHAR 'j'

#define IR_L_CHAR 'w'
#define IR_R_CHAR 'e'
#define USOUND_CHAR 'u'
#define GY_Z_CHAR 'g'
#define QTRN_YAW_CHAR 'y'
#define QTRN_ALL_CHAR 'k'

#define LAST_HALT_CHAR 'o'
//#define T2_180R_CHAR '!'
//#define T2_90R_CHAR '?'
//#define T2_O1_CHAR '@'
#define SONNY_MOVE_o1_CHAR '!'
#define SONNY_DODGE_o1_LEFT_CHAR '?'
#define SONNY_DODGE_o1_RIGHT_CHAR '@'
#define SONNY_DODGE_o2_LEFT_AND_HOME_CHAR '#'
#define SONNY_DODGE_o2_RIGHT_AND_HOME_CHAR '$'

// index 9
#define END_CHAR 'z'


#endif /* INC_APP_PARSER_H_ */
