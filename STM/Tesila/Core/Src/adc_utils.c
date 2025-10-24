/* Core/Src/adc_utils.c */
#include "adc_utils.h"
#include "stm32f4xx_hal.h"
#include <math.h>
#include <string.h>

/* Declare the ADC handle that lives in main.c */
extern ADC_HandleTypeDef hadc1;

#ifndef ADC_REGULAR_RANK_1
#define ADC_REGULAR_RANK_1  (1U)
#endif

/* ====== Optional: VDDA via VREFINT factory calibration (STM32F4) ======
 * Note: On STM32F4, VREFINT is factory-calibrated at 3.3 V and stored at 0x1FFF7A2A.
 * Make sure the VREFINT channel is enabled in CubeMX (TempSensor/Vrefint "ON"),
 * otherwise reads may return 0.
 */
#define VREFINT_CAL_ADDR   ((uint16_t*) (0x1FFF7A2AUL))
#define VREFINT_CAL_VREF   (3.3f)   /* Factory reference voltage for the stored cal value */

float IR_ReadVDDA_Vrefint(void)
{
  /* Configure and read ADC_CHANNEL_VREFINT */
  ADC_ChannelConfTypeDef sConfig = {0};
  sConfig.Channel      = ADC_CHANNEL_VREFINT;
  sConfig.Rank         = ADC_REGULAR_RANK_1;
  sConfig.SamplingTime = ADC_SAMPLETIME_480CYCLES; /* Long sample time recommended */

  if (HAL_ADC_ConfigChannel(&hadc1, &sConfig) != HAL_OK) {
    Error_Handler();
  }

  HAL_ADC_Start(&hadc1);
  if (HAL_ADC_PollForConversion(&hadc1, 10) != HAL_OK) {
    HAL_ADC_Stop(&hadc1);
    return 3.3f; /* Fallback */
  }
  uint16_t vref_raw = (uint16_t)HAL_ADC_GetValue(&hadc1);
  HAL_ADC_Stop(&hadc1);

  uint16_t vrefint_cal = *VREFINT_CAL_ADDR;
  if (vref_raw == 0 || vrefint_cal == 0) return 3.3f; /* guard */

  /* VDDA ≈ VREF_CAL_VOLT * (VREFINT_CAL / VREFINT_RAW) */
  float vdda = VREFINT_CAL_VREF * ((float)vrefint_cal / (float)vref_raw);
  return vdda;
}

/**
  * @brief  Reads a single ADC channel on ADC1 in blocking mode.
  * @param  channel: The ADC channel to read (e.g., ADC_CHANNEL_4, ADC_CHANNEL_5).
  * @retval The 12-bit ADC conversion result.
  */
uint16_t ADC1_Read_Channel(uint32_t channel)
{
  ADC_ChannelConfTypeDef sConfig = {0};

  sConfig.Channel      = channel;
  sConfig.Rank         = ADC_REGULAR_RANK_1;
  sConfig.SamplingTime = ADC_SAMPLETIME_480CYCLES; // Match your CubeMX setting

  if (HAL_ADC_ConfigChannel(&hadc1, &sConfig) != HAL_OK) {
    Error_Handler();
  }

  HAL_ADC_Start(&hadc1);
  HAL_ADC_PollForConversion(&hadc1, 10); // 10ms timeout
  uint16_t adc_value = (uint16_t)HAL_ADC_GetValue(&hadc1);
  HAL_ADC_Stop(&hadc1);

  return adc_value;
}

float IR_RawToVolts(uint16_t raw, float vref)
{
  return (vref * (float)raw) / 4095.0f;
}

/* ---------- Accurate volts->cm using LUT + log-log interpolation ----------
 * GP2Y0A21YK0F typical anchors (approx. datasheet curve):
 *  (V,d): (3.0,10), (1.6,20), (1.2,30), (0.9,40), (0.6,60), (0.45,80)
 * The curve is monotonic in ~10–80 cm. We clamp outside.
 */
float IR_VoltsToCm(float v)
{
  /* Hard clamps around trustworthy region */
  if (v <= 0.40f) return 80.0f;  /* Out of range (far) */
  if (v >= 2.80f) return 10.0f;  /* Very near / foldback area */

  static const float V[] = { 3.00f, 1.60f, 1.20f, 0.90f, 0.60f, 0.45f }; /* descending */
  static const float D[] = { 10.0f, 20.0f, 30.0f, 40.0f, 60.0f, 80.0f };
  const int N = (int)(sizeof(V)/sizeof(V[0]));

  /* Find segment: V[i] >= v >= V[i+1] */
  int i = 0;
  while (i < N - 1 && !(v <= V[i] && v >= V[i+1])) i++;
  if (i >= N - 1) {
    /* Shouldn't happen due to early clamps, but guard anyway */
    return (v > V[0]) ? D[0] : D[N - 1];
  }

  /* Linear interpolation in log-log space for better power-law fit */
  float lv  = logf(v);
  float lv0 = logf(V[i]);
  float lv1 = logf(V[i+1]);
  float ld0 = logf(D[i]);
  float ld1 = logf(D[i+1]);

  float t   = (lv - lv0) / (lv1 - lv0);
  float ld  = ld0 + t * (ld1 - ld0);
  float d   = expf(ld);

  /* Final safety clamp */
  if (d < 10.0f) d = 10.0f;
  if (d > 80.0f) d = 80.0f;
  return d;
}
