/* Core/Inc/adc_utils.h */
#ifndef INC_ADC_UTILS_H_
#define INC_ADC_UTILS_H_

#include "main.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  uint32_t tick;
  uint16_t raw;
  float    volts;
  float    cm;
} ir_sample_t;

/* --- ADC helpers --- */
uint16_t ADC1_Read_Channel(uint32_t channel);    // Blocking single conversion
float    IR_RawToVolts(uint16_t raw, float vref);
float    IR_VoltsToCm(float v);

/* Optional: read VDDA from the internal VREFINT (factory-calibrated) */
float    IR_ReadVDDA_Vrefint(void);

/* (Optional) quick utility: read and convert a channel to cm with live VDDA */
static inline float IR_ReadCm_WithVref(uint32_t channel) {
  float vdda = IR_ReadVDDA_Vrefint();
  uint16_t raw = ADC1_Read_Channel(channel);
  float v = IR_RawToVolts(raw, vdda);
  return IR_VoltsToCm(v);
}

#ifdef __cplusplus
}
#endif

#endif /* INC_ADC_UTILS_H_ */
