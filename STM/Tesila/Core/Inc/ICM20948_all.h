#ifndef ICM20948_ALL_H_
#define ICM20948_ALL_H_

/*
 * One-file combined header for ICM-20948 + AK09916
 * - Sensitivity scale factors
 * - Bit helpers
 * - User banks & register maps (both naming styles preserved)
 * - Public API prototypes you listed
 * - Minimal includes
 */

#ifdef __cplusplus
extern "C" {
#endif

/* ------------------- Includes ------------------- */
#include <stdint.h>
#include <stdio.h>
#include "stm32f4xx_hal.h"

/* ------------------- Sensitivity scale factors ------------------- */
#define GRYO_SENSITIVITY_SCALE_FACTOR_250DPS    131.0f
#define GRYO_SENSITIVITY_SCALE_FACTOR_500DPS     65.5f
#define GRYO_SENSITIVITY_SCALE_FACTOR_1000DPS    32.8f
#define GRYO_SENSITIVITY_SCALE_FACTOR_2000DPS    16.4f

#define ACCEL_SENSITIVITY_SCALE_FACTOR_2G     16384.0f
#define ACCEL_SENSITIVITY_SCALE_FACTOR_4G      8192.0f
#define ACCEL_SENSITIVITY_SCALE_FACTOR_8G      4096.0f
#define ACCEL_SENSITIVITY_SCALE_FACTOR_16G     2048.0f

/* AK09916 nominal sensitivity ≈ 0.15 µT/LSB */
#define MAG_SENSITIVITY_SCALE_FACTOR             0.15f

/* ------------------- Axis/Index helpers ------------------- */
#define X 0
#define Y 1
#define Z 2

#define X_HIGH_BYTE 0
#define X_LOW_BYTE  1
#define Y_HIGH_BYTE 2
#define Y_LOW_BYTE  3
#define Z_HIGH_BYTE 4
#define Z_LOW_BYTE  5

/* ------------------- Device settings ------------------- */
#define ICM20948_RESET               0x80
#define ICM20948_DISABLE_SENSORS     0x00
#define ICM20948_ENABLE_SENSORS      0x3F
#define ICM20948_AUTO_SELECT_CLOCK   0x01

/* ------------------- Global bias variable (user-declared elsewhere) ------------------- */
extern float gyroBiasZ;

/* ------------------- Bit helpers ------------------- */
#define BIT_0 0
#define BIT_1 1
#define BIT_2 2
#define BIT_3 3
#define BIT_4 4
#define BIT_5 5
#define BIT_6 6
#define BIT_7 7
#define BIT_8 8

/* ------------------- User banks (short names) ------------------- */
#define USER_BANK_0 0x0
#define USER_BANK_1 0x1
#define USER_BANK_2 0x2
#define USER_BANK_3 0x3

/* ------------------- I2C slave addresses ------------------- */
#define ICM20948__I2C_SLAVE_ADDRESS_1 0x68
#define ICM20948__I2C_SLAVE_ADDRESS_2 0x69
#define AK09916__I2C_SLAVE_ADDRESS    0x0C

/* 7F is bank select in all banks */
#define ICM20948__USER_BANK_ALL__REG_BANK_SEL__REGISTER 0x7F

/* ------------------- USER BANK 0 (verbose naming) ------------------- */
#define ICM20948__USER_BANK_0__WHO_AM_I__REGISTER          0x00
#define ICM20948__USER_BANK_0__USER_CTRL__REGISTER         0x03
#define ICM20948__USER_BANK_0__LP_CONFIG__REGISTER         0x05
#define ICM20948__USER_BANK_0__PWR_MGMT_1__REGISTER        0x06
#define ICM20948__USER_BANK_0__PWR_MGMT_2__REGISTER        0x07
#define ICM20948__USER_BANK_0__INT_PIN_CFG__REGISTER       0x0F
#define ICM20948__USER_BANK_0__INT_ENABLE__REGISTER        0x10
#define ICM20948__USER_BANK_0__INT_ENABLE_1__REGISTER      0x11
#define ICM20948__USER_BANK_0__INT_ENABLE_2__REGISTER      0x12
#define ICM20948__USER_BANK_0__INT_ENABLE_3__REGISTER      0x13
#define ICM20948__USER_BANK_0__I2C_MST_STATUS__REGISTER    0x17
#define ICM20948__USER_BANK_0__INT_STATUS__REGISTER        0x19
#define ICM20948__USER_BANK_0__INT_STATUS_1__REGISTER      0x1A
#define ICM20948__USER_BANK_0__INT_STATUS_2__REGISTER      0x1B
#define ICM20948__USER_BANK_0__INT_STATUS_3__REGISTER      0x1C
#define ICM20948__USER_BANK_0__DELAY_TIMEH__REGISTER       0x28
#define ICM20948__USER_BANK_0__DELAY_TIMEL__REGISTER       0x29
#define ICM20948__USER_BANK_0__ACCEL_XOUT_H__REGISTER      0x2D
#define ICM20948__USER_BANK_0__ACCEL_XOUT_L__REGISTER      0x2E
#define ICM20948__USER_BANK_0__ACCEL_YOUT_H__REGISTER      0x2F
#define ICM20948__USER_BANK_0__ACCEL_YOUT_L__REGISTER      0x30
#define ICM20948__USER_BANK_0__ACCEL_ZOUT_H__REGISTER      0x31
#define ICM20948__USER_BANK_0__ACCEL_ZOUT_L__REGISTER      0x32
#define ICM20948__USER_BANK_0__GYRO_XOUT_H__REGISTER       0x33
#define ICM20948__USER_BANK_0__GYRO_XOUT_L__REGISTER       0x34
#define ICM20948__USER_BANK_0__GYRO_YOUT_H__REGISTER       0x35
#define ICM20948__USER_BANK_0__GYRO_YOUT_L__REGISTER       0x36
#define ICM20948__USER_BANK_0__GYRO_ZOUT_H__REGISTER       0x37
#define ICM20948__USER_BANK_0__GYRO_ZOUT_L__REGISTER       0x38
#define ICM20948__USER_BANK_0__TEMP_OUT_H__REGISTER        0x39
#define ICM20948__USER_BANK_0__TEMP_OUT_L__REGISTER        0x3A
#define ICM20948__USER_BANK_0__EXT_SENS_DATA_00__REGISTER  0x3B
#define ICM20948__USER_BANK_0__EXT_SENS_DATA_23__REGISTER  0x52
#define ICM20948__USER_BANK_0__FIFO_EN_1__REGISTER         0x66
#define ICM20948__USER_BANK_0__FIFO_EN_2__REGISTER         0x67
#define ICM20948__USER_BANK_0__FIFO_RST__REGISTER          0x68
#define ICM20948__USER_BANK_0__FIFO_MODE__REGISTER         0x69
#define ICM20948__USER_BANK_0__FIFO_COUNTH__REGISTER       0x70
#define ICM20948__USER_BANK_0__FIFO_COUNTL__REGISTER       0x71
#define ICM20948__USER_BANK_0__FIFO_R_W__REGISTER          0x72
#define ICM20948__USER_BANK_0__DATA_RDY_STATUS__REGISTER   0x74
#define ICM20948__USER_BANK_0__FIFO_CFG__REGISTER          0x76

/* ------------------- USER BANK 1 (verbose naming) ------------------- */
#define ICM20948__USER_BANK_1__SELF_TEST_X_GYRO__REGISTER  0x02
#define ICM20948__USER_BANK_1__SELF_TEST_Y_GYRO__REGISTER  0x03
#define ICM20948__USER_BANK_1__SELF_TEST_Z_GYRO__REGISTER  0x04
#define ICM20948__USER_BANK_1__SELF_TEST_X_ACCEL__REGISTER 0x0E
#define ICM20948__USER_BANK_1__SELF_TEST_Y_ACCEL__REGISTER 0x0F
#define ICM20948__USER_BANK_1__SELF_TEST_Z_ACCEL__REGISTER 0x10
#define ICM20948__USER_BANK_1__XA_OFFSET_H__REGISTER       0x14
#define ICM20948__USER_BANK_1__XA_OFFSET_L__REGISTER       0x15
#define ICM20948__USER_BANK_1__YA_OFFSET_H__REGISTER       0x17
#define ICM20948__USER_BANK_1__YA_OFFSET_L__REGISTER       0x18
#define ICM20948__USER_BANK_1__ZA_OFFSET_H__REGISTER       0x1A
#define ICM20948__USER_BANK_1__ZA_OFFSET_L__REGISTER       0x1B
#define ICM20948__USER_BANK_1__TIMEBASE_CORRECTION_PLL__REGISTER 0x28

/* ------------------- USER BANK 2 (verbose naming) ------------------- */
#define ICM20948__USER_BANK_2__GYRO_SMPLRT_DIV__REGISTER   0x00
#define ICM20948__USER_BANK_2__GYRO_CONFIG_1__REGISTER     0x01
#define ICM20948__USER_BANK_2__GYRO_CONFIG_2__REGISTER     0x02
#define ICM20948__USER_BANK_2__XG_OFFSET_H__REGISTER       0x03
#define ICM20948__USER_BANK_2__XG_OFFSET_L__REGISTER       0x04
#define ICM20948__USER_BANK_2__YG_OFFSET_H__REGISTER       0x05
#define ICM20948__USER_BANK_2__YG_OFFSET_L__REGISTER       0x06
#define ICM20948__USER_BANK_2__ZG_OFFSET_H__REGISTER       0x07
#define ICM20948__USER_BANK_2__ZG_OFFSET_L__REGISTER       0x08
#define ICM20948__USER_BANK_2__ODR_ALIGN_EN__REGISTER      0x09
#define ICM20948__USER_BANK_2__ACCEL_SMPLRT_DIV_1__REGISTER 0x10
#define ICM20948__USER_BANK_2__ACCEL_SMPLRT_DIV_2__REGISTER 0x11
#define ICM20948__USER_BANK_2__ACCEL_INTEL_CTRL__REGISTER  0x12
#define ICM20948__USER_BANK_2__ACCEL_WOM_THR__REGISTER     0x13
#define ICM20948__USER_BANK_2__ACCEL_CONFIG__REGISTER      0x14
#define ICM20948__USER_BANK_2__ACCEL_CONFIG_2__REGISTER    0x15
#define ICM20948__USER_BANK_2__FSYNC_CONFIG__REGISTER      0x52
#define ICM20948__USER_BANK_2__TEMP_CONFIG__REGISTER       0x53
#define ICM20948__USER_BANK_2__MOD_CTRL_USR__REGISTER      0x54

/* ------------------- USER BANK 3 (verbose naming) ------------------- */
#define ICM20948__USER_BANK_3__I2C_MST_ODR_CONFIG__REGISTER 0x00
#define ICM20948__USER_BANK_3__I2C_MST_CTRL__REGISTER       0x01
#define ICM20948__USER_BANK_3__I2C_MST_DELAY_CTRL__REGISTER 0x02
#define ICM20948__USER_BANK_3__I2C_SLV0_ADDR__REGISTER      0x03
#define ICM20948__USER_BANK_3__I2C_SLV0_REG__REGISTER       0x04
#define ICM20948__USER_BANK_3__I2C_SLV0_CTRL__REGISTER      0x05
#define ICM20948__USER_BANK_3__I2C_SLV0_DO__REGISTER        0x06
#define ICM20948__USER_BANK_3__I2C_SLV1_ADDR__REGISTER      0x07
#define ICM20948__USER_BANK_3__I2C_SLV1_REG__REGISTER       0x08
#define ICM20948__USER_BANK_3__I2C_SLV1_CTRL__REGISTER      0x09
#define ICM20948__USER_BANK_3__I2C_SLV1_DO__REGISTER        0x0A
#define ICM20948__USER_BANK_3__I2C_SLV2_ADDR__REGISTER      0x0B
#define ICM20948__USER_BANK_3__I2C_SLV2_REG__REGISTER       0x0C
#define ICM20948__USER_BANK_3__I2C_SLV2_CTRL__REGISTER      0x0D
#define ICM20948__USER_BANK_3__I2C_SLV2_DO__REGISTER        0x0E
#define ICM20948__USER_BANK_3__I2C_SLV3_ADDR__REGISTER      0x0F
#define ICM20948__USER_BANK_3__I2C_SLV3_REG__REGISTER       0x10
#define ICM20948__USER_BANK_3__I2C_SLV3_CTRL__REGISTER      0x11
#define ICM20948__USER_BANK_3__I2C_SLV3_DO__REGISTER        0x12
#define ICM20948__USER_BANK_3__I2C_SLV4_ADDR__REGISTER      0x13
#define ICM20948__USER_BANK_3__I2C_SLV4_REG__REGISTER       0x14
#define ICM20948__USER_BANK_3__I2C_SLV4_CTRL__REGISTER      0x15
#define ICM20948__USER_BANK_3__I2C_SLV4_DO__REGISTER        0x16
#define ICM20948__USER_BANK_3__I2C_SLV4_DI__REGISTER        0x17

/* ------------------- Alternative short register naming (kept for compatibility) ------------------- */
#define WHO_AM_I_ICM20948   0x00 /* Should return 0xEA */
#define USER_CTRL           0x03
#define LP_CONFIG           0x05
#define PWR_MGMT_1          0x06
#define PWR_MGMT_2          0x07
#define INT_PIN_CFG         0x0F
#define INT_ENABLE          0x10
#define INT_ENABLE_1        0x11
#define INT_ENABLE_2        0x12
#define INT_ENABLE_3        0x13
#define I2C_MST_STATUS      0x17
#define INT_STATUS          0x19
#define DELAY_TIMEH         0x28
#define DELAY_TIMEL         0x29
#define ACCEL_XOUT_H        0x2D
#define ACCEL_XOUT_L        0x2E
#define ACCEL_YOUT_H        0x2F
#define ACCEL_YOUT_L        0x30
#define ACCEL_ZOUT_H        0x31
#define ACCEL_ZOUT_L        0x32
#define GYRO_XOUT_H         0x33
#define GYRO_XOUT_L         0x34
#define GYRO_YOUT_H         0x35
#define GYRO_YOUT_L         0x36
#define GYRO_ZOUT_H         0x37
#define GYRO_ZOUT_L         0x38
#define TEMP_OUT_H          0x39
#define TEMP_OUT_L          0x3A
#define EXT_SENS_DATA_00    0x3B
#define EXT_SENS_DATA_01    0x3C
#define EXT_SENS_DATA_02    0x3D
#define EXT_SENS_DATA_03    0x3E
#define EXT_SENS_DATA_04    0x3F
#define EXT_SENS_DATA_05    0x40
#define EXT_SENS_DATA_06    0x41
#define EXT_SENS_DATA_07    0x42
#define EXT_SENS_DATA_08    0x43
#define EXT_SENS_DATA_09    0x44
#define EXT_SENS_DATA_10    0x45
#define EXT_SENS_DATA_11    0x46
#define EXT_SENS_DATA_12    0x47
#define EXT_SENS_DATA_13    0x48
#define EXT_SENS_DATA_14    0x49
#define EXT_SENS_DATA_15    0x4A
#define EXT_SENS_DATA_16    0x4B
#define EXT_SENS_DATA_17    0x4C
#define EXT_SENS_DATA_18    0x4D
#define EXT_SENS_DATA_19    0x4E
#define EXT_SENS_DATA_20    0x4F
#define EXT_SENS_DATA_21    0x50
#define EXT_SENS_DATA_22    0x51
#define EXT_SENS_DATA_23    0x52
#define FIFO_EN_1           0x66
#define FIFO_EN_2           0x67
#define FIFO_RST            0x68
#define FIFO_MODE           0x69
#define FIFO_COUNTH         0x70
#define FIFO_COUNTL         0x71
#define FIFO_R_W            0x72
#define DATA_RDY_STATUS     0x74
#define FIFO_CFG            0x76
#define REG_BANK_SEL        0x7F

/* ------------------- Banked config bits ------------------- */
#define CLKSEL_INTERNAL_20MHZ_1 0x0
#define CLKSEL_AUTO_SELECT      0x1
#define CLKSEL_INTERNAL_20MHZ_2 0x6
#define CLKSEL_STOP_CLOCK       0x7

#define CLKSEL_BIT              BIT_0

/* Gyro config #1 (bank 2) */
#define GYRO_FULL_SCALE_250DPS   0
#define GYRO_FULL_SCALE_500DPS   1
#define GYRO_FULL_SCALE_1000DPS  2
#define GYRO_FULL_SCALE_2000DPS  3

#define EN_GRYO_DLPF             1
#define BYPASS_GRYO_DLPF         0

#define GYRO_DLPFCFG_BIT         BIT_3
#define GYRO_FS_SEL_BIT          BIT_1
#define GYRO_FCHOICE_BIT         BIT_0

/* Accel config (bank 2) */
#define ACCEL_DLPFCFG_BIT        BIT_3
#define ACCEL_FS_SEL_BIT         BIT_1
#define ACCEL_FCHOICE_BIT        BIT_0

#define ACCEL_FULL_SCALE_2G      0
#define ACCEL_FULL_SCALE_4G      1
#define ACCEL_FULL_SCALE_8G      2
#define ACCEL_FULL_SCALE_16G     3

/* ------------------- AK09916 registers ------------------- */
#define AK09916__XOUT_L__REGISTER  0x11
#define AK09916__XOUT_H__REGISTER  0x12
#define AK09916__YOUT_L__REGISTER  0x13
#define AK09916__YOUT_H__REGISTER  0x14
#define AK09916__ZOUT_L__REGISTER  0x15
#define AK09916__ZOUT_H__REGISTER  0x16
#define AK09916__ST2__REGISTER     0x18
#define AK09916__CNTL2__REGISTER   0x31
#define AK09916__CNTL3__REGISTER   0x32

/* Convenient aliases from your earlier header (mode values) */
#define REG_ADD_MAG_WIA1    0x00
#define REG_VAL_MAG_WIA1    0x48
#define REG_ADD_MAG_WIA2    0x01
#define REG_VAL_MAG_WIA2    0x09
#define REG_ADD_MAG_ST2     0x10
#define REG_ADD_MAG_DATA    0x11
#define REG_ADD_MAG_CNTL2   0x31
#define REG_VAL_MAG_MODE_PD     0x00
#define REG_VAL_MAG_MODE_SM     0x01
#define REG_VAL_MAG_MODE_10HZ   0x02
#define REG_VAL_MAG_MODE_20HZ   0x04
#define REG_VAL_MAG_MODE_50HZ   0x05
#define REG_VAL_MAG_MODE_100HZ  0x08
#define REG_VAL_MAG_MODE_ST     0x10

#define MAG_DATA_LEN 6

/* ------------------- Orientation/helpers config ------------------- */
/* Enable/disable magnetometer contribution in your fusion */
#ifndef IMU_USE_MAG
#define IMU_USE_MAG 1
#endif

/* Task/update cadence hints (ms) */
#ifndef IMU_UPDATE_PERIOD_MS
#define IMU_UPDATE_PERIOD_MS 10U
#endif

#ifndef IMU_MAG_UPDATE_PERIOD_MS
#define IMU_MAG_UPDATE_PERIOD_MS 100U
#endif

/* ------------------- Public API (as you listed) ------------------- */

/* Device checks */
uint8_t ICM20948_isI2cAddress1(I2C_HandleTypeDef *hi2c);
uint8_t ICM20948_isI2cAddress2(I2C_HandleTypeDef *hi2c);

/* Initialization */
typedef struct {
    /* I2C handle (optional; some APIs use it) */
    I2C_HandleTypeDef *i2cHandle;

    /* Raw/converted sensor caches (your code reads these in places) */
    float acc[3];           // m/s^2
    float gyro[3];          // rad/s or dps depending on your driver; main uses index [2]
    float mag[3];           // µT if you scale, or counts if not (doesn’t matter for main.c)

    /* Extras kept for compatibility with older modules */
    float temp_C;
    float acc_bias[3];
    float gyro_bias[3];
    float mag_bias[3];
    float mag_scale[3];
    float q[4];             // quaternion if you use one elsewhere
} ICM20948;

void ICM20948_init(I2C_HandleTypeDef *hi2c,
                   uint8_t selectI2cAddress,
                   uint8_t selectGyroSensitivity,
                   uint8_t selectAccelSensitivity);

/* Sensor reads */
void ICM20948_readGyroscope_Z(I2C_HandleTypeDef *hi2c,
                              uint8_t selectI2cAddress,
                              uint8_t selectGyroSensitivity,
                              float *gyroZ);

void ICM20948_readAccelerometer_all(I2C_HandleTypeDef *hi2c,
                                    uint8_t selectI2cAddress,
                                    uint8_t selectAccelSensitivity,
                                    float readings[3]);

void ICM20948_readMagnetometer_XY(I2C_HandleTypeDef *hi2c,
                                  float magXY[2]);

/* Orientation helpers (single-writer model) */
void IMU_Update(I2C_HandleTypeDef *hi2c,
                uint8_t selectI2cAddress,
                uint8_t selectGyroSensitivity);

float IMU_GetYawLatest(void);
float IMU_GetGyroZLatest(void);

/* Back-compat shim that still performs IO (not recommended in multi-task setups) */
float IMU_GetYaw(I2C_HandleTypeDef *hi2c,
                 uint8_t selectI2cAddress,
                 uint8_t selectGyroSensitivity);

void IMU_CalibrateGyroZ(I2C_HandleTypeDef *hi2c,
                        uint8_t selectI2cAddress,
                        uint8_t selectGyroSensitivity);

#ifdef __cplusplus
}
#endif

#endif /* ICM20948_ALL_H_ */
