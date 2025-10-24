/* USER CODE BEGIN Header */
/**
  ******************************************************************************
  * @file           : main.c
  * @brief          : Main program body
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
/* Includes ------------------------------------------------------------------*/
#include "main.h"
#include "cmsis_os.h"

/* Private includes ----------------------------------------------------------*/
/* USER CODE BEGIN Includes */
// --- Use volatile for variables shared with an ISR ---
volatile uint8_t  capture_state = 0; // 0=idle, 1=rising edge captured
volatile uint32_t capture_val1 = 0;
volatile uint32_t capture_val2 = 0;
volatile uint32_t pulse_width = 0;
static volatile uint8_t is_capture_done = 0; // Flag to signal completion from ISR to task

volatile float IR_Left_cm = 0.0f;
volatile float IR_Right_cm = 0.0f;
volatile float initial_ultrasound_distance = 0.0f; // in main.c

// NEW variable specifically for the Front Ultrasonic distance
volatile float Front_Distance_cm = 0.0f;
#define TRIG_PORT GPIOB
#define TRIG_PIN  GPIO_PIN_15
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#define _USE_MATH_DEFINES // <-- ADD THIS LINE
#include <math.h>         // <-- ADD THIS LINE
#include "motion.h"
#include <ICM20948_all.h>
#include "app_parser.h"
#include "adc_utils.h"   // add this with the other includes

/* USER CODE END Includes */

/* Private typedef -----------------------------------------------------------*/
/* USER CODE BEGIN PTD */

/* USER CODE END PTD */

/* Private define ------------------------------------------------------------*/
/* USER CODE BEGIN PD */

/* USER CODE END PD */

/* Private macro -------------------------------------------------------------*/
/* USER CODE BEGIN PM */

/* USER CODE END PM */

/* Private variables ---------------------------------------------------------*/
ADC_HandleTypeDef hadc1;

I2C_HandleTypeDef hi2c2;

TIM_HandleTypeDef htim1;
TIM_HandleTypeDef htim2;
TIM_HandleTypeDef htim3;
TIM_HandleTypeDef htim4;
TIM_HandleTypeDef htim5;
TIM_HandleTypeDef htim12;

UART_HandleTypeDef huart1;
UART_HandleTypeDef huart3;
DMA_HandleTypeDef hdma_usart3_rx;

/* Definitions for defaultTask */
osThreadId_t defaultTaskHandle;
const osThreadAttr_t defaultTask_attributes = {
  .name = "defaultTask",
  .stack_size = 512 * 4,
  .priority = (osPriority_t) osPriorityNormal,
};
/* Definitions for motorTask */
osThreadId_t motorTaskHandle;
const osThreadAttr_t motorTask_attributes = {
  .name = "motorTask",
  .stack_size = 512 * 4,
  .priority = (osPriority_t) osPriorityNormal,
};
/* Definitions for OLEDTask */
osThreadId_t OLEDTaskHandle;
const osThreadAttr_t OLEDTask_attributes = {
  .name = "OLEDTask",
  .stack_size = 512 * 4,
  .priority = (osPriority_t) osPriorityLow,
};
/* Definitions for gyroTask */
osThreadId_t gyroTaskHandle;
const osThreadAttr_t gyroTask_attributes = {
  .name = "gyroTask",
  .stack_size = 128 * 4,
  .priority = (osPriority_t) osPriorityLow,
};
/* Definitions for ultrasonicTask */
osThreadId_t ultrasonicTaskHandle;
const osThreadAttr_t ultrasonicTask_attributes = {
  .name = "ultrasonicTask",
  .stack_size = 128 * 4,
  .priority = (osPriority_t) osPriorityLow,
};
/* Definitions for communicateTask */
osThreadId_t communicateTaskHandle;
const osThreadAttr_t communicateTask_attributes = {
  .name = "communicateTask",
  .stack_size = 128 * 4,
  .priority = (osPriority_t) osPriorityLow,
};
/* Definitions for encoderRTask */
osThreadId_t encoderRTaskHandle;
const osThreadAttr_t encoderRTask_attributes = {
  .name = "encoderRTask",
  .stack_size = 128 * 4,
  .priority = (osPriority_t) osPriorityLow,
};
/* Definitions for encoderLTask */
osThreadId_t encoderLTaskHandle;
const osThreadAttr_t encoderLTask_attributes = {
  .name = "encoderLTask",
  .stack_size = 128 * 4,
  .priority = (osPriority_t) osPriorityLow,
};
/* Definitions for IR_Task */
osThreadId_t IR_TaskHandle;
const osThreadAttr_t IR_Task_attributes = {
  .name = "IR_Task",
  .stack_size = 256 * 4,
  .priority = (osPriority_t) osPriorityLow,
};
/* USER CODE BEGIN PV */
// Message Queues
osMessageQueueId_t motor_cmd_queue;      // NEW: For sending commands to the motor task
osMessageQueueId_t uart_rx_msg_queue;    // NEW: For receiving raw UART frames in the parser task

// These variables are shared between tasks and the motion module
volatile int32_t encoder_count_A = 0;
volatile int32_t encoder_count_D = 0;

volatile ICM20948 imu_sensor;
volatile float pitch = 0.0f;
volatile float roll = 0.0f;
volatile float yaw = 0.0f;
extern volatile int32_t last_servo_pulse;

// Ultrasonic sensor variables
volatile uint32_t ic_val1 = 0;
volatile uint32_t ic_val2 = 0;
// UART receive buffer
volatile uint8_t uart_dma_rx_buffer[10]; // NEW: DMA buffer for UART
/* USER CODE END PV */


/* Private function prototypes -----------------------------------------------*/
void SystemClock_Config(void);
static void MX_GPIO_Init(void);
static void MX_DMA_Init(void);
static void MX_TIM1_Init(void);
static void MX_TIM2_Init(void);
static void MX_TIM4_Init(void);
static void MX_USART1_UART_Init(void);
static void MX_TIM5_Init(void);
static void MX_I2C2_Init(void);
static void MX_USART3_UART_Init(void);
static void MX_TIM3_Init(void);
static void MX_TIM12_Init(void);
static void MX_ADC1_Init(void);
void StartDefaultTask(void *argument);
void StartMotorTask(void *argument);
void StartOLEDTask(void *argument);
void StartGyroTask(void *argument);
void StartUltrasonicTask(void *argument);
void StartCommunicateTask(void *argument);
void StartEncoderRTask(void *argument);
void StartEncoderLTask(void *argument);
void StartIR_Task(void *argument);

/* USER CODE BEGIN PFP */

/* USER CODE END PFP */

/* Private user code ---------------------------------------------------------*/
/* USER CODE BEGIN 0 */
void DWT_Delay_Init(void)
	{
	  // Enable the DWT peripheral
	  CoreDebug->DEMCR |= CoreDebug_DEMCR_TRCENA_Msk;

	  // Reset the cycle counter
	  DWT->CYCCNT = 0;

	  // Start the cycle counter
	  DWT->CTRL |= DWT_CTRL_CYCCNTENA_Msk;
	}

/* USER CODE END 0 */

/**
  * @brief  The application entry point.
  * @retval int
  */
int main(void)
{

  /* USER CODE BEGIN 1 */
	/**
	  * @brief  Enables the DWT Cycle Counter for high-precision delays.
	  * @retval None
	  *
	  *
	  */
	DWT_Delay_Init();
  /* USER CODE END 1 */

  /* MCU Configuration--------------------------------------------------------*/

  /* Reset of all peripherals, Initializes the Flash interface and the Systick. */
  HAL_Init();

  /* USER CODE BEGIN Init */

  /* USER CODE END Init */

  /* Configure the system clock */
  SystemClock_Config();

  /* USER CODE BEGIN SysInit */

  /* USER CODE END SysInit */

  /* Initialize all configured peripherals */
  MX_GPIO_Init();
  MX_DMA_Init();
  MX_TIM1_Init();
  MX_TIM2_Init();
  MX_TIM4_Init();
  MX_USART1_UART_Init();
  MX_TIM5_Init();
  MX_I2C2_Init();
  MX_USART3_UART_Init();
  MX_TIM3_Init();
  MX_TIM12_Init();
  MX_ADC1_Init();
  /* USER CODE BEGIN 2 */
  /* USER CODE END 2 */

  /* Init scheduler */
  osKernelInitialize();

  /* USER CODE BEGIN RTOS_MUTEX */
  /* add mutexes, ... */
  /* USER CODE END RTOS_MUTEX */

  /* USER CODE BEGIN RTOS_SEMAPHORES */
  /* add semaphores, ... */
  /* USER CODE END RTOS_SEMAPHORES */

  /* USER CODE BEGIN RTOS_TIMERS */
  /* start timers, add new ones, ... */
  /* USER CODE END RTOS_TIMERS */

  /* USER CODE BEGIN RTOS_QUEUES */
   // NEW: Create the message queues
   motor_cmd_queue = osMessageQueueNew(10, sizeof(MOTION_PKT_t), NULL);
   uart_rx_msg_queue = osMessageQueueNew(5, sizeof(AppMessage_t), NULL);
  /* USER CODE END RTOS_QUEUES */

  /* Create the thread(s) */
  /* creation of defaultTask */
  defaultTaskHandle = osThreadNew(StartDefaultTask, NULL, &defaultTask_attributes);

  /* creation of motorTask */
  motorTaskHandle = osThreadNew(StartMotorTask, NULL, &motorTask_attributes);

  /* creation of OLEDTask */
  OLEDTaskHandle = osThreadNew(StartOLEDTask, NULL, &OLEDTask_attributes);

  /* creation of gyroTask */
  gyroTaskHandle = osThreadNew(StartGyroTask, NULL, &gyroTask_attributes);

  /* creation of ultrasonicTask */
  ultrasonicTaskHandle = osThreadNew(StartUltrasonicTask, NULL, &ultrasonicTask_attributes);

  /* creation of communicateTask */
  communicateTaskHandle = osThreadNew(StartCommunicateTask, NULL, &communicateTask_attributes);

  /* creation of encoderRTask */
  encoderRTaskHandle = osThreadNew(StartEncoderRTask, NULL, &encoderRTask_attributes);

  /* creation of encoderLTask */
  encoderLTaskHandle = osThreadNew(StartEncoderLTask, NULL, &encoderLTask_attributes);

  /* creation of IR_Task */
  IR_TaskHandle = osThreadNew(StartIR_Task, NULL, &IR_Task_attributes);

  /* USER CODE BEGIN RTOS_THREADS */
  /* add threads, ... */
  /* USER CODE END RTOS_THREADS */

  /* USER CODE BEGIN RTOS_EVENTS */
  /* add events, ... */
  /* USER CODE END RTOS_EVENTS */

  /* Start scheduler */
  osKernelStart();

  /* We should never get here as control is now taken by the scheduler */

  /* Infinite loop */
  /* USER CODE BEGIN WHILE */
  while (1)
  {
    /* USER CODE END WHILE */

    /* USER CODE BEGIN 3 */
  }
  /* USER CODE END 3 */
}

/**
  * @brief System Clock Configuration
  * @retval None
  */
void SystemClock_Config(void)
{
  RCC_OscInitTypeDef RCC_OscInitStruct = {0};
  RCC_ClkInitTypeDef RCC_ClkInitStruct = {0};

  /** Configure the main internal regulator output voltage
  */
  __HAL_RCC_PWR_CLK_ENABLE();
  __HAL_PWR_VOLTAGESCALING_CONFIG(PWR_REGULATOR_VOLTAGE_SCALE1);

  /** Initializes the RCC Oscillators according to the specified parameters
  * in the RCC_OscInitTypeDef structure.
  */
  RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSI;
  RCC_OscInitStruct.HSIState = RCC_HSI_ON;
  RCC_OscInitStruct.HSICalibrationValue = RCC_HSICALIBRATION_DEFAULT;
  RCC_OscInitStruct.PLL.PLLState = RCC_PLL_NONE;
  if (HAL_RCC_OscConfig(&RCC_OscInitStruct) != HAL_OK)
  {
    Error_Handler();
  }

  /** Initializes the CPU, AHB and APB buses clocks
  */
  RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK|RCC_CLOCKTYPE_SYSCLK
                              |RCC_CLOCKTYPE_PCLK1|RCC_CLOCKTYPE_PCLK2;
  RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_HSI;
  RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
  RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV1;
  RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV1;

  if (HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_0) != HAL_OK)
  {
    Error_Handler();
  }
}

/**
  * @brief ADC1 Initialization Function
  * @param None
  * @retval None
  */
static void MX_ADC1_Init(void)
{

  /* USER CODE BEGIN ADC1_Init 0 */
  /* USER CODE END ADC1_Init 0 */

  ADC_ChannelConfTypeDef sConfig = {0};

  /* USER CODE BEGIN ADC1_Init 1 */
  /* USER CODE END ADC1_Init 1 */

  /** Configure the global features of the ADC (Clock, Resolution, Data Alignment and number of conversion)
  */
  hadc1.Instance = ADC1;
  hadc1.Init.ClockPrescaler        = ADC_CLOCK_SYNC_PCLK_DIV2;
  hadc1.Init.Resolution            = ADC_RESOLUTION_12B;
  hadc1.Init.ScanConvMode          = DISABLE;
  hadc1.Init.ContinuousConvMode    = DISABLE;
  hadc1.Init.DiscontinuousConvMode = DISABLE;
  hadc1.Init.ExternalTrigConvEdge  = ADC_EXTERNALTRIGCONVEDGE_NONE;
  hadc1.Init.ExternalTrigConv      = ADC_SOFTWARE_START;
  hadc1.Init.DataAlign             = ADC_DATAALIGN_RIGHT;
  hadc1.Init.NbrOfConversion       = 1;
  hadc1.Init.DMAContinuousRequests = DISABLE;
  hadc1.Init.EOCSelection          = ADC_EOC_SINGLE_CONV;
  if (HAL_ADC_Init(&hadc1) != HAL_OK)
  {
    Error_Handler();
  }

  /* === Enable internal channels (VREFINT / TempSensor) === */
  /* Do this once after HAL_ADC_Init. This powers the internal buffer so that
     ADC_CHANNEL_VREFINT returns a valid conversion value. */
  __HAL_RCC_ADC1_CLK_ENABLE();          // <<< NEW (harmless if already enabled)
  ADC->CCR |= ADC_CCR_TSVREFE;          // <<< NEW: enable VREFINT (and TempSensor)
  HAL_Delay(1);                         // <<< NEW: tiny settle time (~us is enough, 1ms is safe)

  /** Configure for a regular external channel (your default) */
  sConfig.Channel      = ADC_CHANNEL_5;          // PA5
  sConfig.Rank         = 1;
  sConfig.SamplingTime = ADC_SAMPLETIME_480CYCLES;  // keep long sample time
  if (HAL_ADC_ConfigChannel(&hadc1, &sConfig) != HAL_OK)
  {
    Error_Handler();
  }
}


/**
  * @brief I2C2 Initialization Function
  * @param None
  * @retval None
  */
static void MX_I2C2_Init(void)
{

  /* USER CODE BEGIN I2C2_Init 0 */

  /* USER CODE END I2C2_Init 0 */

  /* USER CODE BEGIN I2C2_Init 1 */

  /* USER CODE END I2C2_Init 1 */
  hi2c2.Instance = I2C2;
  hi2c2.Init.ClockSpeed = 100000;
  hi2c2.Init.DutyCycle = I2C_DUTYCYCLE_2;
  hi2c2.Init.OwnAddress1 = 0;
  hi2c2.Init.AddressingMode = I2C_ADDRESSINGMODE_7BIT;
  hi2c2.Init.DualAddressMode = I2C_DUALADDRESS_DISABLE;
  hi2c2.Init.OwnAddress2 = 0;
  hi2c2.Init.GeneralCallMode = I2C_GENERALCALL_DISABLE;
  hi2c2.Init.NoStretchMode = I2C_NOSTRETCH_DISABLE;
  if (HAL_I2C_Init(&hi2c2) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN I2C2_Init 2 */

  /* USER CODE END I2C2_Init 2 */

}

/**
  * @brief TIM1 Initialization Function
  * @param None
  * @retval None
  */
static void MX_TIM1_Init(void)
{

  /* USER CODE BEGIN TIM1_Init 0 */

  /* USER CODE END TIM1_Init 0 */

  TIM_MasterConfigTypeDef sMasterConfig = {0};
  TIM_OC_InitTypeDef sConfigOC = {0};
  TIM_BreakDeadTimeConfigTypeDef sBreakDeadTimeConfig = {0};

  /* USER CODE BEGIN TIM1_Init 1 */

  /* USER CODE END TIM1_Init 1 */
  htim1.Instance = TIM1;
  htim1.Init.Prescaler = 0;
  htim1.Init.CounterMode = TIM_COUNTERMODE_UP;
  htim1.Init.Period = 7199;
  htim1.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1;
  htim1.Init.RepetitionCounter = 0;
  htim1.Init.AutoReloadPreload = TIM_AUTORELOAD_PRELOAD_DISABLE;
  if (HAL_TIM_PWM_Init(&htim1) != HAL_OK)
  {
    Error_Handler();
  }
  sMasterConfig.MasterOutputTrigger = TIM_TRGO_RESET;
  sMasterConfig.MasterSlaveMode = TIM_MASTERSLAVEMODE_DISABLE;
  if (HAL_TIMEx_MasterConfigSynchronization(&htim1, &sMasterConfig) != HAL_OK)
  {
    Error_Handler();
  }
  sConfigOC.OCMode = TIM_OCMODE_PWM1;
  sConfigOC.Pulse = 0;
  sConfigOC.OCPolarity = TIM_OCPOLARITY_LOW;
  sConfigOC.OCNPolarity = TIM_OCNPOLARITY_HIGH;
  sConfigOC.OCFastMode = TIM_OCFAST_DISABLE;
  sConfigOC.OCIdleState = TIM_OCIDLESTATE_RESET;
  sConfigOC.OCNIdleState = TIM_OCNIDLESTATE_RESET;
  if (HAL_TIM_PWM_ConfigChannel(&htim1, &sConfigOC, TIM_CHANNEL_3) != HAL_OK)
  {
    Error_Handler();
  }
  if (HAL_TIM_PWM_ConfigChannel(&htim1, &sConfigOC, TIM_CHANNEL_4) != HAL_OK)
  {
    Error_Handler();
  }
  sBreakDeadTimeConfig.OffStateRunMode = TIM_OSSR_DISABLE;
  sBreakDeadTimeConfig.OffStateIDLEMode = TIM_OSSI_DISABLE;
  sBreakDeadTimeConfig.LockLevel = TIM_LOCKLEVEL_OFF;
  sBreakDeadTimeConfig.DeadTime = 0;
  sBreakDeadTimeConfig.BreakState = TIM_BREAK_DISABLE;
  sBreakDeadTimeConfig.BreakPolarity = TIM_BREAKPOLARITY_HIGH;
  sBreakDeadTimeConfig.AutomaticOutput = TIM_AUTOMATICOUTPUT_DISABLE;
  if (HAL_TIMEx_ConfigBreakDeadTime(&htim1, &sBreakDeadTimeConfig) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN TIM1_Init 2 */

  /* USER CODE END TIM1_Init 2 */
  HAL_TIM_MspPostInit(&htim1);

}

/**
  * @brief TIM2 Initialization Function
  * @param None
  * @retval None
  */
static void MX_TIM2_Init(void)
{

  /* USER CODE BEGIN TIM2_Init 0 */

  /* USER CODE END TIM2_Init 0 */

  TIM_Encoder_InitTypeDef sConfig = {0};
  TIM_MasterConfigTypeDef sMasterConfig = {0};

  /* USER CODE BEGIN TIM2_Init 1 */

  /* USER CODE END TIM2_Init 1 */
  htim2.Instance = TIM2;
  htim2.Init.Prescaler = 0;
  htim2.Init.CounterMode = TIM_COUNTERMODE_UP;
  htim2.Init.Period = 65535;
  htim2.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1;
  htim2.Init.AutoReloadPreload = TIM_AUTORELOAD_PRELOAD_DISABLE;
  sConfig.EncoderMode = TIM_ENCODERMODE_TI12;
  sConfig.IC1Polarity = TIM_ICPOLARITY_RISING;
  sConfig.IC1Selection = TIM_ICSELECTION_DIRECTTI;
  sConfig.IC1Prescaler = TIM_ICPSC_DIV1;
  sConfig.IC1Filter = 10;
  sConfig.IC2Polarity = TIM_ICPOLARITY_RISING;
  sConfig.IC2Selection = TIM_ICSELECTION_DIRECTTI;
  sConfig.IC2Prescaler = TIM_ICPSC_DIV1;
  sConfig.IC2Filter = 10;
  if (HAL_TIM_Encoder_Init(&htim2, &sConfig) != HAL_OK)
  {
    Error_Handler();
  }
  sMasterConfig.MasterOutputTrigger = TIM_TRGO_RESET;
  sMasterConfig.MasterSlaveMode = TIM_MASTERSLAVEMODE_DISABLE;
  if (HAL_TIMEx_MasterConfigSynchronization(&htim2, &sMasterConfig) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN TIM2_Init 2 */

  /* USER CODE END TIM2_Init 2 */

}

/**
  * @brief TIM3 Initialization Function
  * @param None
  * @retval None
  */
static void MX_TIM3_Init(void)
{

  /* USER CODE BEGIN TIM3_Init 0 */

  /* USER CODE END TIM3_Init 0 */

  TIM_ClockConfigTypeDef sClockSourceConfig = {0};
  TIM_MasterConfigTypeDef sMasterConfig = {0};
  TIM_OC_InitTypeDef sConfigOC = {0};

  /* USER CODE BEGIN TIM3_Init 1 */

  /* USER CODE END TIM3_Init 1 */
  htim3.Instance = TIM3;
  htim3.Init.Prescaler = 159;
  htim3.Init.CounterMode = TIM_COUNTERMODE_UP;
  htim3.Init.Period = 1999;
  htim3.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1;
  htim3.Init.AutoReloadPreload = TIM_AUTORELOAD_PRELOAD_DISABLE;
  if (HAL_TIM_Base_Init(&htim3) != HAL_OK)
  {
    Error_Handler();
  }
  sClockSourceConfig.ClockSource = TIM_CLOCKSOURCE_INTERNAL;
  if (HAL_TIM_ConfigClockSource(&htim3, &sClockSourceConfig) != HAL_OK)
  {
    Error_Handler();
  }
  if (HAL_TIM_PWM_Init(&htim3) != HAL_OK)
  {
    Error_Handler();
  }
  sMasterConfig.MasterOutputTrigger = TIM_TRGO_RESET;
  sMasterConfig.MasterSlaveMode = TIM_MASTERSLAVEMODE_DISABLE;
  if (HAL_TIMEx_MasterConfigSynchronization(&htim3, &sMasterConfig) != HAL_OK)
  {
    Error_Handler();
  }
  sConfigOC.OCMode = TIM_OCMODE_PWM1;
  sConfigOC.Pulse = 0;
  sConfigOC.OCPolarity = TIM_OCPOLARITY_HIGH;
  sConfigOC.OCFastMode = TIM_OCFAST_DISABLE;
  if (HAL_TIM_PWM_ConfigChannel(&htim3, &sConfigOC, TIM_CHANNEL_4) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN TIM3_Init 2 */

  /* USER CODE END TIM3_Init 2 */
  HAL_TIM_MspPostInit(&htim3);

}

/**
  * @brief TIM4 Initialization Function
  * @param None
  * @retval None
  */
static void MX_TIM4_Init(void)
{

  /* USER CODE BEGIN TIM4_Init 0 */

  /* USER CODE END TIM4_Init 0 */

  TIM_MasterConfigTypeDef sMasterConfig = {0};
  TIM_OC_InitTypeDef sConfigOC = {0};

  /* USER CODE BEGIN TIM4_Init 1 */

  /* USER CODE END TIM4_Init 1 */
  htim4.Instance = TIM4;
  htim4.Init.Prescaler = 0;
  htim4.Init.CounterMode = TIM_COUNTERMODE_UP;
  htim4.Init.Period = 7199;
  htim4.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1;
  htim4.Init.AutoReloadPreload = TIM_AUTORELOAD_PRELOAD_DISABLE;
  if (HAL_TIM_PWM_Init(&htim4) != HAL_OK)
  {
    Error_Handler();
  }
  sMasterConfig.MasterOutputTrigger = TIM_TRGO_RESET;
  sMasterConfig.MasterSlaveMode = TIM_MASTERSLAVEMODE_DISABLE;
  if (HAL_TIMEx_MasterConfigSynchronization(&htim4, &sMasterConfig) != HAL_OK)
  {
    Error_Handler();
  }
  sConfigOC.OCMode = TIM_OCMODE_PWM1;
  sConfigOC.Pulse = 0;
  sConfigOC.OCPolarity = TIM_OCPOLARITY_LOW;
  sConfigOC.OCFastMode = TIM_OCFAST_DISABLE;
  if (HAL_TIM_PWM_ConfigChannel(&htim4, &sConfigOC, TIM_CHANNEL_3) != HAL_OK)
  {
    Error_Handler();
  }
  if (HAL_TIM_PWM_ConfigChannel(&htim4, &sConfigOC, TIM_CHANNEL_4) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN TIM4_Init 2 */

  /* USER CODE END TIM4_Init 2 */
  HAL_TIM_MspPostInit(&htim4);

}

/**
  * @brief TIM5 Initialization Function
  * @param None
  * @retval None
  */
static void MX_TIM5_Init(void)
{

  /* USER CODE BEGIN TIM5_Init 0 */

  /* USER CODE END TIM5_Init 0 */

  TIM_Encoder_InitTypeDef sConfig = {0};
  TIM_MasterConfigTypeDef sMasterConfig = {0};

  /* USER CODE BEGIN TIM5_Init 1 */

  /* USER CODE END TIM5_Init 1 */
  htim5.Instance = TIM5;
  htim5.Init.Prescaler = 0;
  htim5.Init.CounterMode = TIM_COUNTERMODE_UP;
  htim5.Init.Period = 65535;
  htim5.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1;
  htim5.Init.AutoReloadPreload = TIM_AUTORELOAD_PRELOAD_DISABLE;
  sConfig.EncoderMode = TIM_ENCODERMODE_TI12;
  sConfig.IC1Polarity = TIM_ICPOLARITY_RISING;
  sConfig.IC1Selection = TIM_ICSELECTION_DIRECTTI;
  sConfig.IC1Prescaler = TIM_ICPSC_DIV1;
  sConfig.IC1Filter = 10;
  sConfig.IC2Polarity = TIM_ICPOLARITY_RISING;
  sConfig.IC2Selection = TIM_ICSELECTION_DIRECTTI;
  sConfig.IC2Prescaler = TIM_ICPSC_DIV1;
  sConfig.IC2Filter = 0;
  if (HAL_TIM_Encoder_Init(&htim5, &sConfig) != HAL_OK)
  {
    Error_Handler();
  }
  sMasterConfig.MasterOutputTrigger = TIM_TRGO_RESET;
  sMasterConfig.MasterSlaveMode = TIM_MASTERSLAVEMODE_DISABLE;
  if (HAL_TIMEx_MasterConfigSynchronization(&htim5, &sMasterConfig) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN TIM5_Init 2 */

  /* USER CODE END TIM5_Init 2 */

}

/**
  * @brief TIM12 Initialization Function
  * @param None
  * @retval None
  */
static void MX_TIM12_Init(void)
{

  /* USER CODE BEGIN TIM12_Init 0 */

  /* USER CODE END TIM12_Init 0 */

  TIM_ClockConfigTypeDef sClockSourceConfig = {0};
  TIM_IC_InitTypeDef sConfigIC = {0};

  /* USER CODE BEGIN TIM12_Init 1 */

  /* USER CODE END TIM12_Init 1 */
  htim12.Instance = TIM12;
  htim12.Init.Prescaler = 15;
  htim12.Init.CounterMode = TIM_COUNTERMODE_UP;
  htim12.Init.Period = 65535;
  htim12.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1;
  htim12.Init.AutoReloadPreload = TIM_AUTORELOAD_PRELOAD_DISABLE;
  if (HAL_TIM_Base_Init(&htim12) != HAL_OK)
  {
    Error_Handler();
  }
  sClockSourceConfig.ClockSource = TIM_CLOCKSOURCE_INTERNAL;
  if (HAL_TIM_ConfigClockSource(&htim12, &sClockSourceConfig) != HAL_OK)
  {
    Error_Handler();
  }
  if (HAL_TIM_IC_Init(&htim12) != HAL_OK)
  {
    Error_Handler();
  }
  sConfigIC.ICPolarity = TIM_INPUTCHANNELPOLARITY_RISING;
  sConfigIC.ICSelection = TIM_ICSELECTION_DIRECTTI;
  sConfigIC.ICPrescaler = TIM_ICPSC_DIV1;
  sConfigIC.ICFilter = 8;
  if (HAL_TIM_IC_ConfigChannel(&htim12, &sConfigIC, TIM_CHANNEL_1) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN TIM12_Init 2 */

  /* USER CODE END TIM12_Init 2 */

}

/**
  * @brief USART1 Initialization Function
  * @param None
  * @retval None
  */
static void MX_USART1_UART_Init(void)
{

  /* USER CODE BEGIN USART1_Init 0 */

  /* USER CODE END USART1_Init 0 */

  /* USER CODE BEGIN USART1_Init 1 */

  /* USER CODE END USART1_Init 1 */
  huart1.Instance = USART1;
  huart1.Init.BaudRate = 115200;
  huart1.Init.WordLength = UART_WORDLENGTH_8B;
  huart1.Init.StopBits = UART_STOPBITS_1;
  huart1.Init.Parity = UART_PARITY_NONE;
  huart1.Init.Mode = UART_MODE_TX_RX;
  huart1.Init.HwFlowCtl = UART_HWCONTROL_NONE;
  huart1.Init.OverSampling = UART_OVERSAMPLING_16;
  if (HAL_UART_Init(&huart1) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN USART1_Init 2 */

  /* USER CODE END USART1_Init 2 */

}

/**
  * @brief USART3 Initialization Function
  * @param None
  * @retval None
  */
static void MX_USART3_UART_Init(void)
{

  /* USER CODE BEGIN USART3_Init 0 */

  /* USER CODE END USART3_Init 0 */

  /* USER CODE BEGIN USART3_Init 1 */

  /* USER CODE END USART3_Init 1 */
  huart3.Instance = USART3;
  huart3.Init.BaudRate = 115200;
  huart3.Init.WordLength = UART_WORDLENGTH_8B;
  huart3.Init.StopBits = UART_STOPBITS_1;
  huart3.Init.Parity = UART_PARITY_NONE;
  huart3.Init.Mode = UART_MODE_TX_RX;
  huart3.Init.HwFlowCtl = UART_HWCONTROL_NONE;
  huart3.Init.OverSampling = UART_OVERSAMPLING_16;
  if (HAL_UART_Init(&huart3) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN USART3_Init 2 */
  // NEW: Start listening for a 10-byte packet using DMA
  HAL_UART_Receive_DMA(&huart3, (uint8_t*)uart_dma_rx_buffer, 10);
  /* USER CODE END USART3_Init 2 */

}

/**
  * Enable DMA controller clock
  */
static void MX_DMA_Init(void)
{

  /* DMA controller clock enable */
  __HAL_RCC_DMA1_CLK_ENABLE();

  /* DMA interrupt init */
  /* DMA1_Stream1_IRQn interrupt configuration */
  HAL_NVIC_SetPriority(DMA1_Stream1_IRQn, 5, 0);
  HAL_NVIC_EnableIRQ(DMA1_Stream1_IRQn);

}

/**
  * @brief GPIO Initialization Function
  * @param None
  * @retval None
  */
static void MX_GPIO_Init(void)
{
  GPIO_InitTypeDef GPIO_InitStruct = {0};
  /* USER CODE BEGIN MX_GPIO_Init_1 */

  /* USER CODE END MX_GPIO_Init_1 */

  /* GPIO Ports Clock Enable */
  __HAL_RCC_GPIOA_CLK_ENABLE();
  __HAL_RCC_GPIOE_CLK_ENABLE();
  __HAL_RCC_GPIOB_CLK_ENABLE();
  __HAL_RCC_GPIOD_CLK_ENABLE();
  __HAL_RCC_GPIOC_CLK_ENABLE();

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(LED3_GPIO_Port, LED3_Pin, GPIO_PIN_RESET);

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(GPIOB, GPIO_PIN_15, GPIO_PIN_RESET);

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(GPIOD, OLED_DC_Pin|OLED_RST_Pin|OLED_SDA_Pin|OLED_SCL_Pin, GPIO_PIN_RESET);

  /*Configure GPIO pin : LED3_Pin */
  GPIO_InitStruct.Pin = LED3_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(LED3_GPIO_Port, &GPIO_InitStruct);

  /*Configure GPIO pin : PB15 */
  GPIO_InitStruct.Pin = GPIO_PIN_15;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(GPIOB, &GPIO_InitStruct);

  /*Configure GPIO pins : OLED_DC_Pin OLED_RST_Pin OLED_SDA_Pin OLED_SCL_Pin */
  GPIO_InitStruct.Pin = OLED_DC_Pin|OLED_RST_Pin|OLED_SDA_Pin|OLED_SCL_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(GPIOD, &GPIO_InitStruct);

  /*Configure GPIO pin : UESR_BUTTON_Pin */
  GPIO_InitStruct.Pin = UESR_BUTTON_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_INPUT;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  HAL_GPIO_Init(UESR_BUTTON_GPIO_Port, &GPIO_InitStruct);

  /* USER CODE BEGIN MX_GPIO_Init_2 */

  /* USER CODE END MX_GPIO_Init_2 */
}

/* USER CODE BEGIN 4 */
/**
* @brief TIM_IC MSP Initialization
*/
void HAL_TIM_IC_MspInit(TIM_HandleTypeDef* htim_ic)
{
  GPIO_InitTypeDef GPIO_InitStruct = {0};
  if(htim_ic->Instance==TIM12)
  {
    __HAL_RCC_TIM12_CLK_ENABLE();
    __HAL_RCC_GPIOB_CLK_ENABLE();
    /**TIM12 GPIO Configuration: PB14 -> TIM12_CH1 */
    GPIO_InitStruct.Pin = GPIO_PIN_14;
    GPIO_InitStruct.Mode = GPIO_MODE_AF_PP;
    GPIO_InitStruct.Pull = GPIO_NOPULL;
    GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
    GPIO_InitStruct.Alternate = GPIO_AF9_TIM12;
    HAL_GPIO_Init(GPIOB, &GPIO_InitStruct);

    HAL_NVIC_SetPriority(TIM8_BRK_TIM12_IRQn, 5, 0);
    HAL_NVIC_EnableIRQ(TIM8_BRK_TIM12_IRQn);
  }
}

/**
  * @brief  Input Capture callback in non-blocking mode
  */
// --- Make sure these are declared globally as volatile ---
// volatile uint8_t  is_capture_done = 0;
// volatile uint32_t ic_val1 = 0;
// volatile uint32_t ic_val2 = 0;
// volatile uint32_t pulse_width = 0;

void HAL_TIM_IC_CaptureCallback(TIM_HandleTypeDef *htim)
{
    if (htim->Instance == TIM12 && htim->Channel == HAL_TIM_ACTIVE_CHANNEL_1)
    {
        if (capture_state == 0) // rising edge just captured
        {
            capture_val1 = HAL_TIM_ReadCapturedValue(htim, TIM_CHANNEL_1);
            capture_state = 1;

            // --- SAFE SWITCH to FALLING ---
            __HAL_TIM_DISABLE_IT(htim, TIM_IT_CC1);
            __HAL_TIM_SET_CAPTUREPOLARITY(htim, TIM_CHANNEL_1, TIM_INPUTCHANNELPOLARITY_FALLING);
            __HAL_TIM_CLEAR_FLAG(htim, TIM_FLAG_CC1);
            __HAL_TIM_ENABLE_IT(htim, TIM_IT_CC1);
        }
        else // capture_state == 1 → falling edge
        {
            capture_val2 = HAL_TIM_ReadCapturedValue(htim, TIM_CHANNEL_1);

            // --- Compute pulse width (handle wrap correctly) ---
            if (capture_val2 >= capture_val1)
                pulse_width = capture_val2 - capture_val1;
            else
                pulse_width = (0xFFFFu - capture_val1) + capture_val2 + 1u;

            is_capture_done = 1;
            capture_state = 0;

            // --- Restore to RISING for next cycle (safe) ---
            __HAL_TIM_DISABLE_IT(htim, TIM_IT_CC1);
            __HAL_TIM_SET_CAPTUREPOLARITY(htim, TIM_CHANNEL_1, TIM_INPUTCHANNELPOLARITY_RISING);
            __HAL_TIM_CLEAR_FLAG(htim, TIM_FLAG_CC1);
            __HAL_TIM_ENABLE_IT(htim, TIM_IT_CC1);

            HAL_TIM_IC_Stop_IT(&htim12, TIM_CHANNEL_1);
        }
    }
}


void DWT_Delay_us(volatile uint32_t microseconds)
{
  uint32_t clk_cycle_start = DWT->CYCCNT;
  /* Go to number of cycles for system */
  microseconds *= (HAL_RCC_GetHCLKFreq() / 1000000);
  /* Wait until the cycles are counted */
  while ((DWT->CYCCNT - clk_cycle_start) < microseconds);
}

void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart)
{
  if (huart->Instance == USART3) {
      AppMessage_t msg;
      memcpy(msg.buffer, (void*)uart_dma_rx_buffer, 10);
      msg.length = 10;

      // Send the received data to the communication task's queue from within the ISR
      osMessageQueuePut(uart_rx_msg_queue, &msg, 0, 0);

      // Re-enable the DMA reception for the next packet
      HAL_UART_Receive_DMA(&huart3, (uint8_t*)uart_dma_rx_buffer, 10);
  }
}


void vApplicationStackOverflowHook( TaskHandle_t xTask, char *pcTaskName )
{
   /* This function will be called if a task overflows its stack. */
   // You can put a breakpoint here to see which task caused the overflow.
   while(1)
   {
       // Flash the LED very, very fast to signal a stack overflow.
       HAL_GPIO_WritePin(LED3_GPIO_Port, LED3_Pin, GPIO_PIN_SET); // <-- REPLACE LED_ON()
       for(int i=0; i<100000; i++);
       HAL_GPIO_WritePin(LED3_GPIO_Port, LED3_Pin, GPIO_PIN_RESET); // <-- REPLACE LED_OFF()
       for(int i=0; i<100000; i++);
   }
}
/* USER CODE END 4 */

/* USER CODE BEGIN Header_StartDefaultTask */
/**
  * @brief  Function implementing the defaultTask thread.
  * @param  argument: Not used
  * @retval None
  */
/* USER CODE END Header_StartDefaultTask */
void StartDefaultTask(void *argument)
{
  /* USER CODE BEGIN 5 */
  /* Infinite loop */
  for(;;)
  {
    osDelay(1);
  }
  /* USER CODE END 5 */
}

/* USER CODE BEGIN Header_StartMotorTask */
/**
* @brief Function implementing the motorTask thread.
* @param argument: Not used
* @retval None
*/
/* USER CODE END Header_StartMotorTask */
uint8_t get_button_clicks(void)
{
    const int debounce_delay_ms = 50;
    const int double_click_window_ms = 300;
    int click_count = 0;

    // Wait for the initial button press
    while (HAL_GPIO_ReadPin(UESR_BUTTON_GPIO_Port, UESR_BUTTON_Pin) != GPIO_PIN_RESET)
    {
        osDelay(10); // Wait for a press
    }

    osDelay(debounce_delay_ms); // Debounce the press

    // Wait for the button to be released
    while (HAL_GPIO_ReadPin(UESR_BUTTON_GPIO_Port, UESR_BUTTON_Pin) == GPIO_PIN_RESET)
    {
        osDelay(10);
    }

    click_count = 1;
    osDelay(debounce_delay_ms); // Debounce the release

    // Start a window of time to check for a second click
    uint32_t start_time = osKernelGetTickCount();
    while ((osKernelGetTickCount() - start_time) < double_click_window_ms)
    {
        if (HAL_GPIO_ReadPin(UESR_BUTTON_GPIO_Port, UESR_BUTTON_Pin) == GPIO_PIN_RESET)
        {
            click_count = 2; // Second click detected
            break;
        }
        osDelay(10);
    }

    return click_count;
}



void StartMotorTask(void *argument)
{
  /* USER CODE BEGIN StartMotorTask */
  MOTION_PKT_t received_pkt;
  osStatus_t status;

  Motion_Init();
  osDelay(500);

  for (;;)
  {
    // Wait for a command to arrive in the queue from the parser
    status = osMessageQueueGet(motor_cmd_queue, &received_pkt, NULL, osWaitForever);


    if (status == osOK)
    {
      switch (received_pkt.cmd)
      {
        case MOVE_FWD:
          initial_ultrasound_distance = Front_Distance_cm;
          Motion_Sonny_Move_To_O1();
          break;

        case MOVE_BWD:
        	if((float)received_pkt.arg == 0.0f){
				Motion_Sonny_Dodge_O2_Left_And_Home(initial_ultrasound_distance);
        	}
        	if((float)received_pkt.arg == 1.0f){
        		Motion_Sonny_Dodge_O2_Right_And_Home(initial_ultrasound_distance);
        	}
         break;

        case MOVE_LEFT_FWD:
          if (!received_pkt.linear)
          { // In-place pivot turn
            Motion_Sonny_Dodge_O1_Left();
          }
          else
          { // Forward arc turn
        	Motion_Sonny_Dodge_O1_Left();
          }
          break;

        case MOVE_RIGHT_FWD:
          if (!received_pkt.linear)
          { // In-place pivot turn
        	 Motion_Sonny_Dodge_O1_Right();
          }
          else
          { // Forward arc turn
        	 Motion_Sonny_Dodge_O1_Right();
          }
          break;

        case MOVE_LEFT_BWD:
          if (!received_pkt.linear)
          { // Backward pivot is same as forward
            Motion_Turn_Angle(-(float)received_pkt.arg);
          }
          else
          { // Backward arc turn
            int steer_deg = 50;
            int32_t pwm_bw = received_pkt.is_crawl ? 1200 : 2500;
            Motion_Turn_Angle_Arc_Backward(-(float)received_pkt.arg, steer_deg, pwm_bw);
          }
          break;

        case MOVE_RIGHT_BWD:
          if (!received_pkt.linear)
          { // Backward pivot is same as forward
            Motion_Turn_Angle(+(float)received_pkt.arg);
          }
          else
          { // Backward arc turn
            int steer_deg = 50;
            int32_t pwm_bw = received_pkt.is_crawl ? 1200 : 2500;
            Motion_Turn_Angle_Arc_Backward(+(float)received_pkt.arg, steer_deg, pwm_bw);
          }
          break;

        case MOVE_HALT:
          Motion_Stop();
          break;

        default:
          Motion_Stop();
          break;
      }

      // Run the motion controller until it reports done
      if (received_pkt.cmd != MOVE_HALT)
      {
        while (Motion_Is_Busy())
        {
          Motion_Process();
          osDelay(20); // main control loop cadence
        }
        // Short dwell after a move/turn finishes
        osDelay(150);

      }
    }
  }
  /* USER CODE END StartMotorTask */
}
/* USER CODE BEGIN Header_StartOLEDTask */
/**
* @brief Function implementing the OLEDTask thread.
* @param argument: Not used
* @retval None
*/
/* USER CODE END Header_StartOLEDTask */
void StartOLEDTask(void *argument)
{
  /* USER CODE BEGIN StartOLEDTask */
  extern void OLED_Init(void);
  extern void OLED_Clear(void);
  extern void OLED_ShowString(uint8_t x, uint8_t y, const uint8_t *p);
  extern void OLED_Refresh_Gram(void);
  extern volatile float IR_Left_cm;  // <-- Add extern for new variables
  extern volatile float IR_Right_cm; // <-- Add extern for new variable
  extern volatile float Front_Distance_cm;

  OLED_Init(); // Initialize OLED

  // In StartOLEDTask
  for(;;)
  {
      char buffer[40]; // Give it a little more space just in case

      // Display Yaw Angle


      // Display Gyro Z-axis raw data
      snprintf(buffer, sizeof(buffer), "Gyro Z: %.2f", IMU_GetGyroZLatest());
      OLED_ShowString(0, 0, (uint8_t*)buffer);

      snprintf(buffer, sizeof(buffer), "FDist: %.1f cm", Front_Distance_cm);
            OLED_ShowString(0, 12, (uint8_t*)buffer);

       snprintf(buffer, sizeof(buffer), "L:%.1f R:%.1f", IR_Left_cm, IR_Right_cm);
            OLED_ShowString(0, 24, (uint8_t*)buffer); // Display on the last line

      OLED_Refresh_Gram();
      osDelay(200);
  }
  /* USER CODE END StartOLEDTask */
}

/* USER CODE BEGIN Header_StartGyroTask */
/**
* @brief Function implementing the gyroTask thread.
* @param argument: Not used
* @retval None
*/
/* USER CODE END Header_StartGyroTask */
void StartGyroTask(void *argument)
{
  /* USER CODE BEGIN StartGyroTask */

  // ---- 1) Choose device params ----
  // I2C address select: 0 -> 0x68, 1 -> 0x69
  const uint8_t addrSel   = 0;                       // use 0x68
  const uint8_t gyroFS    = GYRO_FULL_SCALE_500DPS;  // good resolution / low noise
  const uint8_t accelFS   = ACCEL_FULL_SCALE_2G;     // (used by init only)

  // ---- 2) Init IMU + AK09916 and calibrate gyro Z ----
  ICM20948_init(&hi2c2, addrSel, gyroFS, accelFS);
  HAL_Delay(300);
  IMU_CalibrateGyroZ(&hi2c2, addrSel, gyroFS);  // ~1s quick bias, robot still

  // ---- 3) Run at fixed cadence using FreeRTOS tick ----
  TickType_t lastWake = xTaskGetTickCount();

  // Optional: zero outputs
  yaw = 0.0f; pitch = 0.0f; roll = 0.0f;

  for (;;)
  {
    // 3a) Update fused yaw (gyro-only with optional mag assist inside IMU_Update)
    IMU_Update(&hi2c2, addrSel, gyroFS);

    // 3b) Publish yaw for the rest of the system
    yaw = IMU_GetYawLatest() ;


    // If your robot expects CW to be +yaw and you observe the opposite,
    // uncomment the next line once (don’t flip elsewhere):
    // yaw = -yaw;

    // We’re not estimating pitch/roll in this lightweight path:
    pitch = 0.0f;
    roll  = 0.0f;

    // 3c) Sleep exactly IMU_UPDATE_PERIOD_MS (from your header)
    vTaskDelayUntil(&lastWake, pdMS_TO_TICKS(IMU_UPDATE_PERIOD_MS));
  }

  /* USER CODE END StartGyroTask */
}

/* USER CODE BEGIN Header_StartUltrasonicTask */
/**
* @brief Function implementing the ultrasonicTask thread.
* @param argument: Not used
* @retval None
*/
/* USER CODE END Header_StartUltrasonicTask */
void StartUltrasonicTask(void *argument)
{
  /* USER CODE BEGIN StartUltrasonicTask */
  // Initialize the DWT for microsecond delays. Do this once.
  DWT_Delay_Init();

  /* Infinite loop */
  for(;;)
  {
    // --- 1. Prepare for a new measurement ---
    is_capture_done = 0; // Reset the completion flag

    // --- 2. Send the 10us Trigger Pulse ---
    HAL_GPIO_WritePin(TRIG_PORT, TRIG_PIN, GPIO_PIN_SET);
    DWT_Delay_us(10);
    HAL_GPIO_WritePin(TRIG_PORT, TRIG_PIN, GPIO_PIN_RESET);

    // --- 3. Start listening for the echo pulse ---
    // Enable the timer and its interrupt. The callback will handle the rest.
    HAL_TIM_IC_Start_IT(&htim12, TIM_CHANNEL_1);

    // --- 4. Wait for the measurement to complete (with a timeout) ---
    // Give the sensor up to 40ms to get a reading.
    osDelay(40);

    // --- 5. Calculate Distance if measurement was successful ---
    if (is_capture_done)
    {
      /*
       * Timer Clock Tick = 1 / (APB1_Timer_Clock / (Prescaler + 1))
       * Assuming APB1 Timer Clock = 16 MHz and Prescaler = 15:
       * Tick Frequency = 16,000,000 / (15 + 1) = 1,000,000 Hz
       * So, 1 tick = 1 microsecond (us).
       * Therefore, the value in 'pulse_width' is the time of flight in microseconds.
       * PLEASE VERIFY YOUR APB1 TIMER CLOCK FREQUENCY IN CUBEMX.
      */

      // Distance (cm) = (Time_of_flight_in_us / 58.3)
      // The constant 58.3 is derived from (2 / speed_of_sound_in_cm_per_us)
      Front_Distance_cm = (float)pulse_width / 58.3f;
    }
    else
    {
      // The capture did not complete within the 40ms timeout.
      // This means the object is out of range or there was no echo.
      // You can set a specific value to indicate this error state.
      Front_Distance_cm = -1.0f; // Or a large value like 999.0f
    }

    // --- 6. Delay before the next measurement cycle ---
    // Wait a bit to prevent echo interference from the previous ping.
    osDelay(60);
  }
  /* USER CODE END StartUltrasonicTask */
}

/* USER CODE BEGIN Header_StartCommunicateTask */
/**
* @brief Function implementing the communicateTask thread.
* @param argument: Not used
* @retval None
*/
/* USER CODE END Header_StartCommunicateTask */
void StartCommunicateTask(void *argument)
{
  /* USER CODE BEGIN StartCommunicateTask */
  // NEW: Initialize the parser with the motor command queue
  Parser_Init(motor_cmd_queue);

  /* Infinite loop */
  for(;;)
  {
    // The new Parser_Process function will now handle everything.
    // It internally waits on the uart_rx_msg_queue.
    Parser_Process();
  }
  /* USER CODE END StartCommunicateTask */
}

/* USER CODE BEGIN Header_StartEncoderRTask */
/**
* @brief Function implementing the encoderRTask thread.
* @param argument: Not used
* @retval None
*/
/* USER CODE END Header_StartEncoderRTask */
void StartEncoderRTask(void *argument)
{
  /* USER CODE BEGIN StartEncoderRTask */
  uint16_t last_counter = 0;
  HAL_TIM_Encoder_Start(&htim5, TIM_CHANNEL_ALL); // TIM5 for Encoder D (Right)
  /* Infinite loop */
  for(;;)
  {
    uint16_t current_counter = __HAL_TIM_GET_COUNTER(&htim5);
    // This handles timer overflow correctly
    int16_t diff = current_counter - last_counter;
    encoder_count_D += diff;
    last_counter = current_counter;
    osDelay(10); // Update 100 times per second
  }
  /* USER CODE END StartEncoderRTask */
}

/* USER CODE BEGIN Header_StartEncoderLTask */
/**
* @brief Function implementing the encoderLTask thread.
* @param argument: Not used
* @retval None
*/
/* USER CODE END Header_StartEncoderLTask */
void StartEncoderLTask(void *argument)
{
  /* USER CODE BEGIN StartEncoderLTask */
  uint16_t last_counter = 0;
  HAL_TIM_Encoder_Start(&htim2, TIM_CHANNEL_ALL); // TIM2 for Encoder A (Left)
  /* Infinite loop */
  for(;;)
  {
    uint16_t current_counter = __HAL_TIM_GET_COUNTER(&htim2);
    int16_t diff = current_counter - last_counter;
    encoder_count_A += diff;
    last_counter = current_counter;
    osDelay(10); // Update 100 times per second
  }
  /* USER CODE END StartEncoderLTask */
}

/* USER CODE BEGIN Header_StartIR_Task */
/**
* @brief Function implementing the IR_Task thread.
* @param argument: Not used
* @retval None
*/
/* USER CODE END Header_StartIR_Task */
void StartIR_Task(void *argument)
{
  /* USER CODE BEGIN StartIR_Task */

  for(;;)
  {
    /* Read VDDA once per cycle (or every N cycles if you prefer) */
    float vdda_now = IR_ReadVDDA_Vrefint();

    // --- LEFT (PA5 / ADC1_IN5) ---
    uint32_t sum_left = 0;
    for (int i = 0; i < 16; i++) {
      sum_left += ADC1_Read_Channel(ADC_CHANNEL_5);
      osDelay(1);
    }
    uint16_t avg_left = sum_left / 16;
    float volts_left  = IR_RawToVolts(avg_left, vdda_now);
    IR_Left_cm        = IR_VoltsToCm(volts_left);

    // --- RIGHT (PA4 / ADC1_IN4) ---
    uint32_t sum_right = 0;
    for (int i = 0; i < 16; i++) {
      sum_right += ADC1_Read_Channel(ADC_CHANNEL_4);
      osDelay(1);
    }
    uint16_t avg_right = sum_right / 16;
    float volts_right  = IR_RawToVolts(avg_right, vdda_now);
    IR_Right_cm        = IR_VoltsToCm(volts_right);

    osDelay(20);
  }
  /* USER CODE END StartIR_Task */
}

/**
  * @brief  Period elapsed callback in non blocking mode
  * @note   This function is called  when TIM6 interrupt took place, inside
  * HAL_TIM_IRQHandler(). It makes a direct call to HAL_IncTick() to increment
  * a global variable "uwTick" used as application time base.
  * @param  htim : TIM handle
  * @retval None
  */
void HAL_TIM_PeriodElapsedCallback(TIM_HandleTypeDef *htim)
{
  /* USER CODE BEGIN Callback 0 */

  /* USER CODE END Callback 0 */
  if (htim->Instance == TIM6)
  {
    HAL_IncTick();
  }
  /* USER CODE BEGIN Callback 1 */

  /* USER CODE END Callback 1 */
}

/**
  * @brief  This function is executed in case of error occurrence.
  * @retval None
  */
void Error_Handler(void)
{
  /* USER CODE BEGIN Error_Handler_Debug */
  /* User can add his own implementation to report the HAL error return state */
  __disable_irq();
  while (1)
  {
  }
  /* USER CODE END Error_Handler_Debug */
}
#ifdef USE_FULL_ASSERT
/**
  * @brief  Reports the name of the source file and the source line number
  *         where the assert_param error has occurred.
  * @param  file: pointer to the source file name
  * @param  line: assert_param error line source number
  * @retval None
  */
void assert_failed(uint8_t *file, uint32_t line)
{
  /* USER CODE BEGIN 6 */
  /* User can add his own implementation to report the file name and line number,
     ex: printf("Wrong parameters value: file %s on line %d\r\n", file, line) */
  /* USER CODE END 6 */
}
#endif /* USE_FULL_ASSERT */
