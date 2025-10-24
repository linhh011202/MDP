#include <ICM20948_all.h>
#include <math.h>
#include "stm32f4xx_hal_i2c.h"

#define ALPHA 0.995f

// ------------------- Global state -------------------
float gyroBiasZ = 0.0f;
static float s_integratedYaw = 0.0f;
static float s_cachedYaw = 0.0f;
static float s_cachedGyroZ = 0.0f;
#if IMU_USE_MAG
static float s_lastMagYaw = 0.0f;
static uint32_t s_magElapsedMs = IMU_MAG_UPDATE_PERIOD_MS;
static uint8_t s_hasMagYaw = 0U;
#endif

HAL_StatusTypeDef _ICM20948_SelectUserBank(I2C_HandleTypeDef * hi2c, uint8_t const selectI2cAddress, int userBankNum) {
	HAL_StatusTypeDef status = HAL_OK;
	uint8_t writeData = userBankNum << BIT_4;
	uint8_t deviceI2CAddress = (selectI2cAddress == 0)? ICM20948__I2C_SLAVE_ADDRESS_1: ICM20948__I2C_SLAVE_ADDRESS_2;

	status = HAL_I2C_Mem_Write(
			hi2c,
			deviceI2CAddress << 1,
			ICM20948__USER_BANK_ALL__REG_BANK_SEL__REGISTER,
			I2C_MEMADD_SIZE_8BIT,
			&writeData,
			I2C_MEMADD_SIZE_8BIT,
			10);

	return status;
}

HAL_StatusTypeDef _ICM20948_WriteByte(I2C_HandleTypeDef * hi2c, uint8_t const selectI2cAddress, uint8_t const registerAddress, uint8_t writeData) {
	HAL_StatusTypeDef status = HAL_OK;
	uint8_t deviceI2CAddress = (selectI2cAddress == 0)? ICM20948__I2C_SLAVE_ADDRESS_1: ICM20948__I2C_SLAVE_ADDRESS_2;

	status = HAL_I2C_Mem_Write(
			hi2c,
			deviceI2CAddress << 1,
			registerAddress,
			I2C_MEMADD_SIZE_8BIT,
			&writeData,
			I2C_MEMADD_SIZE_8BIT,
			10);

	return status;
}

HAL_StatusTypeDef _ICM20948_ReadByte(I2C_HandleTypeDef * hi2c, uint8_t const selectI2cAddress, uint8_t const registerAddress, uint8_t * readData) {
	HAL_StatusTypeDef status = HAL_OK;
	uint8_t deviceI2CAddress = (selectI2cAddress == 0)? ICM20948__I2C_SLAVE_ADDRESS_1: ICM20948__I2C_SLAVE_ADDRESS_2;

	status = HAL_I2C_Mem_Read(
			hi2c,
			deviceI2CAddress << 1,
			registerAddress,
			I2C_MEMADD_SIZE_8BIT,
			readData,
			I2C_MEMADD_SIZE_8BIT,
			10);

	return status;
}

HAL_StatusTypeDef _ICM20948_BrustRead(I2C_HandleTypeDef * hi2c, uint8_t const selectI2cAddress, uint8_t const startAddress, uint16_t const amountOfRegistersToRead, uint8_t * readData) {
	HAL_StatusTypeDef status = HAL_OK;
	uint8_t deviceI2CAddress = (selectI2cAddress == 0)? ICM20948__I2C_SLAVE_ADDRESS_1: ICM20948__I2C_SLAVE_ADDRESS_2;

	status = HAL_I2C_Mem_Read(
			hi2c,
			deviceI2CAddress << 1,
			startAddress,
			I2C_MEMADD_SIZE_8BIT,
			readData,
			amountOfRegistersToRead * I2C_MEMADD_SIZE_8BIT,
			10);

	return status;
}

HAL_StatusTypeDef _AK09916_WriteByte(I2C_HandleTypeDef * hi2c, uint8_t const registerAddress, uint8_t writeData) {
	HAL_StatusTypeDef status = HAL_OK;

	status = HAL_I2C_Mem_Write(
			hi2c,
			AK09916__I2C_SLAVE_ADDRESS << 1,
			registerAddress,
			I2C_MEMADD_SIZE_8BIT,
			&writeData,
			I2C_MEMADD_SIZE_8BIT,
			10);

	return status;
}

HAL_StatusTypeDef _AK09916_ReadByte(I2C_HandleTypeDef * hi2c, uint8_t const registerAddress, uint8_t *readData) {
	HAL_StatusTypeDef status = HAL_OK;

	status = HAL_I2C_Mem_Read(
			hi2c,
			AK09916__I2C_SLAVE_ADDRESS << 1,
			registerAddress,
			I2C_MEMADD_SIZE_8BIT,
			readData,
			I2C_MEMADD_SIZE_8BIT,
			10);

	return status;
}

HAL_StatusTypeDef _AK09916_BrustRead(I2C_HandleTypeDef * hi2c, uint8_t const startAddress, uint16_t const amountOfRegistersToRead, uint8_t * readData) {
	HAL_StatusTypeDef status = HAL_OK;

	status = HAL_I2C_Mem_Read(
			hi2c,
			AK09916__I2C_SLAVE_ADDRESS << 1,
			startAddress,
			I2C_MEMADD_SIZE_8BIT,
			readData,
			amountOfRegistersToRead * I2C_MEMADD_SIZE_8BIT,
			10);

	return status;
}

uint8_t ICM20948_isI2cAddress1(I2C_HandleTypeDef * hi2c) {
	HAL_StatusTypeDef addressStatus = HAL_I2C_IsDeviceReady(hi2c, ICM20948__I2C_SLAVE_ADDRESS_1 << 1, 2, 10);

	if (addressStatus == HAL_OK) {
		return 1;
	}

	return 0;
}

uint8_t ICM20948_isI2cAddress2(I2C_HandleTypeDef * hi2c) {
	HAL_StatusTypeDef addressStatus = HAL_I2C_IsDeviceReady(hi2c, ICM20948__I2C_SLAVE_ADDRESS_2 << 1, 2, 10);

	if (addressStatus == HAL_OK) {
		return 1;
	}

	return 0;
}

void ICM20948_init(I2C_HandleTypeDef * hi2c, uint8_t const selectI2cAddress, uint8_t const selectGyroSensitivity, uint8_t const selectAccelSensitivity) {
	HAL_StatusTypeDef status = HAL_OK;

	status = _ICM20948_SelectUserBank(hi2c, selectI2cAddress, USER_BANK_0);

	status = _ICM20948_WriteByte(
			hi2c,
			selectI2cAddress,
			ICM20948__USER_BANK_0__PWR_MGMT_1__REGISTER,
			ICM20948_RESET);

	HAL_Delay(200);

	status = _ICM20948_WriteByte(
			hi2c,
			selectI2cAddress,
			ICM20948__USER_BANK_0__PWR_MGMT_1__REGISTER,
			ICM20948_AUTO_SELECT_CLOCK);

	//enable both gyroscope and accelerometer
	status = _ICM20948_WriteByte(
			hi2c,
			selectI2cAddress,
			ICM20948__USER_BANK_0__PWR_MGMT_2__REGISTER,
			ICM20948_DISABLE_SENSORS); // For some reason this needs to be tested

	status = _ICM20948_SelectUserBank(hi2c, selectI2cAddress, USER_BANK_2);

	////temperature configuration.
	//	status = _ICM20948_WriteByte(
	//			hi2c,
	//			selectI2cAddress,
	//			ICM20948__USER_BANK_2__TEMP_CONFIG__REGISTER,
	//			0x03);

	//gyroscope sampling rate settings.
	status = _ICM20948_WriteByte(
			hi2c,
			selectI2cAddress,
			ICM20948__USER_BANK_2__GYRO_CONFIG_1__REGISTER,
			0 << GYRO_DLPFCFG_BIT|selectGyroSensitivity << GYRO_FS_SEL_BIT|EN_GRYO_DLPF << GYRO_FCHOICE_BIT);
	status = _ICM20948_WriteByte(
			hi2c,
			selectI2cAddress,
			ICM20948__USER_BANK_2__GYRO_SMPLRT_DIV__REGISTER,
			4);

	//accelerometer sampling rate settings.
	status = _ICM20948_WriteByte(
			hi2c,
			selectI2cAddress,
			ICM20948__USER_BANK_2__ACCEL_CONFIG__REGISTER,
			1 << ACCEL_DLPFCFG_BIT|selectAccelSensitivity << ACCEL_FS_SEL_BIT|0x01 << ACCEL_FCHOICE_BIT);
	status = _ICM20948_WriteByte(
			hi2c,
			selectI2cAddress,
			ICM20948__USER_BANK_2__ACCEL_SMPLRT_DIV_2__REGISTER,
			4);


	status = _ICM20948_SelectUserBank(hi2c, selectI2cAddress, USER_BANK_0);

	status = _ICM20948_WriteByte(
			hi2c,
			selectI2cAddress,
			ICM20948__USER_BANK_0__INT_PIN_CFG__REGISTER,
			0x02); // Don't understand how this works

	status = _AK09916_WriteByte(
			hi2c,
			AK09916__CNTL2__REGISTER,
			0x08);
}

void ICM20948_readGyroscope_Z(I2C_HandleTypeDef * hi2c, uint8_t const selectI2cAddress, uint8_t const selectGyroSensitivity, float *gyroZ) {
	uint8_t readData[2];

//	_ICM20948_SelectUserBank(hi2c, selectI2cAddress, USER_BANK_0);
	_ICM20948_BrustRead(hi2c, selectI2cAddress, ICM20948__USER_BANK_0__GYRO_ZOUT_H__REGISTER, 2, readData);

	int16_t reading = readData[0]<<8 | readData[1];
	*gyroZ = (float) -reading;
	switch (selectGyroSensitivity) {
		case GYRO_FULL_SCALE_250DPS:
			*gyroZ /= GRYO_SENSITIVITY_SCALE_FACTOR_250DPS;
			break;
		case GYRO_FULL_SCALE_500DPS:
			*gyroZ /= GRYO_SENSITIVITY_SCALE_FACTOR_500DPS;
			break;
		case GYRO_FULL_SCALE_1000DPS:
			*gyroZ /= GRYO_SENSITIVITY_SCALE_FACTOR_1000DPS;
			break;
		case GYRO_FULL_SCALE_2000DPS:
			*gyroZ /= GRYO_SENSITIVITY_SCALE_FACTOR_2000DPS;
			break;
	}

}

void ICM20948_readAccelerometer_all(I2C_HandleTypeDef * hi2c, uint8_t const selectI2cAddress, uint8_t const selectAccelSensitivity, float readings[3]) {
	uint8_t readData[6];

//	_ICM20948_SelectUserBank(hi2c, selectI2cAddress, USER_BANK_0);
	_ICM20948_BrustRead(hi2c, selectI2cAddress, ICM20948__USER_BANK_0__ACCEL_XOUT_H__REGISTER, 6, readData);


	int16_t rD_int[3];
	rD_int[X] = readData[X_HIGH_BYTE]<<8|readData[X_LOW_BYTE];
	rD_int[Y] = readData[Y_HIGH_BYTE]<<8|readData[Y_LOW_BYTE];
	rD_int[Z] = readData[Z_HIGH_BYTE]<<8|readData[Z_LOW_BYTE];

	float rD[3];
	rD[X] = (float) rD_int[X];
	rD[Y] = (float) rD_int[Y];
	rD[Z] = (float) rD_int[Z];

	switch (selectAccelSensitivity) {
		case ACCEL_FULL_SCALE_2G:
			readings[X] = rD[X] / ACCEL_SENSITIVITY_SCALE_FACTOR_2G;
			readings[Y] = rD[Y] / ACCEL_SENSITIVITY_SCALE_FACTOR_2G;
			readings[Z] = rD[Z] / ACCEL_SENSITIVITY_SCALE_FACTOR_2G;
			break;
		case ACCEL_FULL_SCALE_4G:
			readings[X] = rD[X] / ACCEL_SENSITIVITY_SCALE_FACTOR_4G;
			readings[Y] = rD[Y] / ACCEL_SENSITIVITY_SCALE_FACTOR_4G;
			readings[Z] = rD[Z] / ACCEL_SENSITIVITY_SCALE_FACTOR_4G;
			break;
		case ACCEL_FULL_SCALE_8G:
			readings[X] = rD[X] / ACCEL_SENSITIVITY_SCALE_FACTOR_8G;
			readings[Y] = rD[Y] / ACCEL_SENSITIVITY_SCALE_FACTOR_8G;
			readings[Z] = rD[Z] / ACCEL_SENSITIVITY_SCALE_FACTOR_8G;
			break;
		case ACCEL_FULL_SCALE_16G:
			readings[X] = rD[X] / ACCEL_SENSITIVITY_SCALE_FACTOR_16G;
			readings[Y] = rD[Y] / ACCEL_SENSITIVITY_SCALE_FACTOR_16G;
			readings[Z] = rD[Z] / ACCEL_SENSITIVITY_SCALE_FACTOR_16G;
			break;
	}
}

void ICM20948_readMagnetometer_XY(I2C_HandleTypeDef * hi2c, float magXY[2]) {
	uint8_t readData[4];
	_AK09916_BrustRead(hi2c, AK09916__XOUT_L__REGISTER, 4, readData);

	//read status register to mark end of data read.
	uint8_t st2;
	_AK09916_ReadByte(hi2c, AK09916__ST2__REGISTER, &st2);
	int16_t reading;
	for (uint8_t i = 0; i < 2; i++) {
		reading = readData[1+2*i]<<8|readData[2*i];
		magXY[i] = reading * MAG_SENSITIVITY_SCALE_FACTOR;
	}
}




void IMU_Update(I2C_HandleTypeDef *hi2c,
				uint8_t selectI2cAddress,
				uint8_t selectGyroSensitivity) {
	float gyroZ = 0.0f;
	ICM20948_readGyroscope_Z(hi2c, selectI2cAddress, selectGyroSensitivity, &gyroZ);
	gyroZ -= gyroBiasZ;

	const float dt = ((float)IMU_UPDATE_PERIOD_MS) / 1000.0f;
	s_integratedYaw += gyroZ * dt;

	if (s_integratedYaw > 180.0f)  s_integratedYaw -= 360.0f;
	if (s_integratedYaw < -180.0f) s_integratedYaw += 360.0f;

	s_cachedYaw = s_integratedYaw;
	s_cachedGyroZ = gyroZ;

#if IMU_USE_MAG
	s_magElapsedMs += IMU_UPDATE_PERIOD_MS;
	if (s_magElapsedMs >= IMU_MAG_UPDATE_PERIOD_MS) {
		float magXY[2];
		ICM20948_readMagnetometer_XY(hi2c, magXY);
		float magYaw = atan2f(magXY[1], magXY[0]) * 180.0f / M_PI;
		if (magYaw > 180.0f)  magYaw -= 360.0f;
		if (magYaw < -180.0f) magYaw += 360.0f;

		s_lastMagYaw = magYaw;
		s_hasMagYaw = 1U;
		s_magElapsedMs = 0U;
	}

	if (s_hasMagYaw) {
		float fusedYaw = ALPHA * s_integratedYaw + (1.0f - ALPHA) * s_lastMagYaw;
		if (fusedYaw > 180.0f)  fusedYaw -= 360.0f;
		if (fusedYaw < -180.0f) fusedYaw += 360.0f;
		s_cachedYaw = fusedYaw;
	}
#endif
}

float IMU_GetYawLatest(void) {
	return s_cachedYaw;
}

float IMU_GetGyroZLatest(void) {
	return s_cachedGyroZ;
}

float IMU_GetYaw(I2C_HandleTypeDef *hi2c, uint8_t selectI2cAddress, uint8_t selectGyroSensitivity) {
	(void)hi2c;
	(void)selectI2cAddress;
	(void)selectGyroSensitivity;
	return IMU_GetYawLatest();
}


void IMU_CalibrateGyroZ(I2C_HandleTypeDef *hi2c, uint8_t addr, uint8_t gyroFS) {
    float sum = 0, gZ;
    for (int i = 0; i < 500; i++) {
        ICM20948_readGyroscope_Z(hi2c, addr, gyroFS, &gZ);
        sum += gZ;
        HAL_Delay(2);
    }
    gyroBiasZ = sum / 500.0f;
	s_integratedYaw = 0.0f;
	s_cachedYaw = 0.0f;
	s_cachedGyroZ = 0.0f;
#if IMU_USE_MAG
	s_lastMagYaw = 0.0f;
	s_magElapsedMs = IMU_MAG_UPDATE_PERIOD_MS;
	s_hasMagYaw = 0U;
#endif
}


