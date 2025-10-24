/* USER CODE BEGIN Header */
/**
  ******************************************************************************
  * @file           : main.h
  * @brief          : Header for main.c file.
  *                   This file contains the common defines of the application.
  ******************************************************************************
  * @attention
  *
  * Copyright (c) 2025 STMicroelectronics.
  * All rights reserved.
  *
  * This software is licensed under terms that can be found in the LICENSE file
  * in the root directory of this software component.
  * If no LICENSE file comes with this software, it is provided AS-IS.
  *
  ******************************************************************************
  */
/* USER CODE END Header */

/* Define to prevent recursive inclusion -------------------------------------*/
#ifndef __MAIN_H
#define __MAIN_H

#ifdef __cplusplus
extern "C" {
#endif

/* Includes ------------------------------------------------------------------*/
#include "stm32f4xx_hal.h"

/* Private includes ----------------------------------------------------------*/
/* USER CODE BEGIN Includes */

/* USER CODE END Includes */

/* Exported types ------------------------------------------------------------*/
/* USER CODE BEGIN ET */

/* USER CODE END ET */

/* Exported constants --------------------------------------------------------*/
/* USER CODE BEGIN EC */

/* USER CODE END EC */

/* Exported macro ------------------------------------------------------------*/
/* USER CODE BEGIN EM */

/* USER CODE END EM */

void HAL_TIM_MspPostInit(TIM_HandleTypeDef *htim);

/* Exported functions prototypes ---------------------------------------------*/
void Error_Handler(void);

/* USER CODE BEGIN EFP */

/* USER CODE END EFP */

/* Private defines -----------------------------------------------------------*/
#define EncoderD_Pin GPIO_PIN_0
#define EncoderD_GPIO_Port GPIOA
#define EncoderDA1_Pin GPIO_PIN_1
#define EncoderDA1_GPIO_Port GPIOA
#define LED3_Pin GPIO_PIN_8
#define LED3_GPIO_Port GPIOE
#define MotorD_IN2_Pin GPIO_PIN_13
#define MotorD_IN2_GPIO_Port GPIOE
#define MotorD_IN1_Pin GPIO_PIN_14
#define MotorD_IN1_GPIO_Port GPIOE
#define OLED_DC_Pin GPIO_PIN_11
#define OLED_DC_GPIO_Port GPIOD
#define OLED_RST_Pin GPIO_PIN_12
#define OLED_RST_GPIO_Port GPIOD
#define OLED_SDA_Pin GPIO_PIN_13
#define OLED_SDA_GPIO_Port GPIOD
#define OLED_SCL_Pin GPIO_PIN_14
#define OLED_SCL_GPIO_Port GPIOD
#define EncoderA_Pin GPIO_PIN_15
#define EncoderA_GPIO_Port GPIOA
#define EncoderAB3_Pin GPIO_PIN_3
#define EncoderAB3_GPIO_Port GPIOB
#define MotorA_IN2_Pin GPIO_PIN_8
#define MotorA_IN2_GPIO_Port GPIOB
#define MotorA_IN1_Pin GPIO_PIN_9
#define MotorA_IN1_GPIO_Port GPIOB
#define UESR_BUTTON_Pin GPIO_PIN_0
#define UESR_BUTTON_GPIO_Port GPIOE

/* USER CODE BEGIN Private defines */
// ❗❗ TUNE THESE VALUES FOR YOUR ROBOT ❗❗
#define WHEEL_DIAMETER_CM       6.5f  // Example: 6.5 cm
#define ENCODER_PPR             330  // Example: 13 pulses * 30 gear ratio
#define ENCODER_TIMER_PERIOD    65535 // From TIM2/TIM5 Init

// Calculated constant
#define WHEEL_CIRCUMFERENCE_CM  (WHEEL_DIAMETER_CM * 3.14159f)

// Max PWM value for the motors
#define MAX_PWM 7199 // From TIM4 Init
/* USER CODE END Private defines */

#ifdef __cplusplus
}
#endif

#endif /* __MAIN_H */
