/*
 * app_parser.c
 *
 * Created on: Oct 4, 2025
 * Author: rombo
 */
#include "app_parser.h"
#include "main.h"
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include "motion.h"
// External variables and handles from your project
extern UART_HandleTypeDef huart3;
extern osMessageQueueId_t uart_rx_msg_queue;
extern volatile float yaw;

// Module-level static variables
static osMessageQueueId_t motor_message_queue; // Will be set by Parser_Init
static AppMessage_t uart_rx_msg;
static MOTION_PKT_t motion_packet; // Re-usable packet

// Private helper function prototypes
static void return_sensor_request_cmd(BUF_CMP_t id);
static bool get_motion_cmd_from_bytes(const BUF_CMP_t *bytes, MOTION_PKT_t *pkt);


/**
 * @brief Initializes the parser with the handle to the motor task's message queue.
 * @param motor_queue The osMessageQueueId_t for the motor task.
 */
void Parser_Init(osMessageQueueId_t motor_queue)
{
    motor_message_queue = motor_queue;
}

/**
 * @brief Processes one incoming message from the UART queue.
 * @note This function is designed to be called repeatedly in a loop from StartCommunicateTask.
 */
void Parser_Process(void)
{
    // Check for a message with a short timeout, so this function doesn't block the system.
    if (osMessageQueueGet(uart_rx_msg_queue, &uart_rx_msg, NULL, 10) == osOK)
    {
        // 1) Frame guard: Check start and end characters
        if (uart_rx_msg.buffer[0] != START_CHAR || uart_rx_msg.buffer[9] != END_CHAR) {
            HAL_UART_Transmit(&huart3, (uint8_t*)NACK_MSG, strlen(NACK_MSG), 100);
            return;
        }

        // 2) Handle Requests (data queries)
        if (uart_rx_msg.buffer[1] == REQ_CHAR) {
            if (uart_rx_msg.buffer[2] == SENSOR_CHAR) {
                return_sensor_request_cmd(uart_rx_msg.buffer[3]);
            } else {
                HAL_UART_Transmit(&huart3, (uint8_t*)NACK_MSG, strlen(NACK_MSG), 100);
            }
            return;
        }

        // 3) Handle Commands (actions)
        if (uart_rx_msg.buffer[1] == CMD_CHAR) {
            switch (uart_rx_msg.buffer[2]) {
                case MOTOR_CHAR:
                    // Try to decode the command into a motion packet
                    if (get_motion_cmd_from_bytes(uart_rx_msg.buffer, &motion_packet)) {
                        // If successful, send it to the motor task
                        osMessageQueuePut(motor_message_queue, &motion_packet, 0, 0);
                        HAL_UART_Transmit(&huart3, (uint8_t*)ACK_MSG, strlen(ACK_MSG), 100);
                    } else {
                        // If decoding fails
                        HAL_UART_Transmit(&huart3, (uint8_t*)NACK_MSG, strlen(NACK_MSG), 100);
                    }
                    break;

                // Placeholder for other command modules
                case SENSOR_CHAR:
                case AUX_CHAR:
                    // Acknowledge but do nothing for now
                    HAL_UART_Transmit(&huart3, (uint8_t*)ACK_MSG, strlen(ACK_MSG), 100);
                    break;

                default:
                    HAL_UART_Transmit(&huart3, (uint8_t*)NACK_MSG, strlen(NACK_MSG), 100);
                    break;
            }
            return;
        }

        // If message type is not REQ or CMD
        HAL_UART_Transmit(&huart3, (uint8_t*)NACK_MSG, strlen(NACK_MSG), 100);
    }
}

static void return_sensor_request_cmd(BUF_CMP_t id)
{
    // ***** THIS IS THE ONLY LINE THAT WAS CHANGED *****
    static char tx_buf[30] = {0};
    const char* term = "\r\n";

    switch (id)
    {
        case GY_Z_CHAR:
        case QTRN_YAW_CHAR:
            snprintf(tx_buf, sizeof(tx_buf) - strlen(term), "%.2f", yaw);
            strcat(tx_buf, term);
            break;

        // --- ADD THIS NEW CASE ---
        case IS_BUSY_CHAR:
            // Call the existing function that checks the motion state
            if (Motion_Is_Busy()) {
                snprintf(tx_buf, sizeof(tx_buf), "1%s", term); // "1" for busy
            } else {
                snprintf(tx_buf, sizeof(tx_buf), "0%s", term); // "0" for not busy
            }
            break;

        default:
            snprintf(tx_buf, sizeof(tx_buf), "%s%s", NACK_MSG, term);
            break;
    }

    HAL_UART_Transmit(&huart3, (uint8_t*)tx_buf, strlen(tx_buf), 100);
}

/**
 * @brief Decodes a 10-byte motion command into your specific MOTION_PKT_t struct.
 * @note This version is matched to your app_parser.h
 */
static bool get_motion_cmd_from_bytes(const BUF_CMP_t *bytes, MOTION_PKT_t *pkt)
{
    // Start by clearing the packet to ensure all bool flags are false by default
    memset(pkt, 0, sizeof(MOTION_PKT_t));

    // --- Robust numeric parse of bytes [4..6] ---
    char arg_str[4];
    memcpy(arg_str, &bytes[4], 3);
    arg_str[3] = '\0';

    char *endp = NULL;
    unsigned long val_ul = strtoul(arg_str, &endp, 10);
    if (endp == arg_str || *endp != '\0' || val_ul > 999UL) {
        if (bytes[3] != HALT_CHAR) return false;
        val_ul = 0;
    }
    pkt->arg = (uint32_t)val_ul; // Matched to your struct's uint32_t

    // --- Decode Opcode ---
    switch (bytes[3])
    {
        case FWD_CHAR:   pkt->cmd = MOVE_FWD;        break;
        case BWD_CHAR:   pkt->cmd = MOVE_BWD;        break;
        case LEFT_CHAR:
            pkt->cmd = (bytes[7] == BWD_CHAR) ? MOVE_LEFT_BWD : MOVE_LEFT_FWD;
            break;
        case RIGHT_CHAR:
            pkt->cmd = (bytes[7] == BWD_CHAR) ? MOVE_RIGHT_BWD : MOVE_RIGHT_FWD;
            break;
        case HALT_CHAR:
            pkt->cmd = MOVE_HALT; pkt->arg = 0;
            break;
        case SONNY_MOVE_o1_CHAR:
        	pkt->cmd = MOVE_SONNY_MOVE_TO_O1; break;
        case SONNY_DODGE_o1_LEFT_CHAR:
                	pkt->cmd = MOVE_SONNY_DODGE_O1_LEFT; break;
        case SONNY_DODGE_o1_RIGHT_CHAR:
                   	pkt->cmd = MOVE_SONNY_DODGE_O1_RIGHT; break;
        case SONNY_DODGE_o2_LEFT_AND_HOME_CHAR:
                	pkt->cmd = MOVE_SONNY_DODGE_O2_LEFT_AND_HOME; break;
        case SONNY_DODGE_o2_RIGHT_AND_HOME_CHAR:
                	pkt->cmd = MOVE_SONNY_DODGE_O2_RIGHT_AND_HOME; break;

        default: return false; // Invalid command character
    }

    // --- Decode Options from bytes [7] and [8] ---
    for (int i = 7; i <= 8; i++) {
        if (bytes[i] == CRAWL_CHAR) {
            pkt->is_crawl = true;
        } else if (bytes[i] == LINEAR_CHAR) {
            pkt->linear = true;
        }
    }

    // pkt->is_absol and pkt->turn_opt are not set by the current Python script,
    // but they are safely initialized to false by the memset() at the top.

    return true;
}
