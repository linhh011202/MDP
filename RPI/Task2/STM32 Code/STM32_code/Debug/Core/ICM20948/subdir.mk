################################################################################
# Automatically-generated file. Do not edit!
# Toolchain: GNU Tools for STM32 (10.3-2021.10)
################################################################################

# Add inputs and outputs from these tool invocations to the build variables 
CPP_SRCS += \
../Core/ICM20948/ICM_20948.cpp 

OBJS += \
./Core/ICM20948/ICM_20948.o 

CPP_DEPS += \
./Core/ICM20948/ICM_20948.d 


# Each subdirectory must supply rules for building sources it contributes
Core/ICM20948/%.o Core/ICM20948/%.su: ../Core/ICM20948/%.cpp Core/ICM20948/subdir.mk
	arm-none-eabi-g++ "$<" -mcpu=cortex-m4 -std=gnu++14 -g3 -DDEBUG -DUSE_HAL_DRIVER -DSTM32F407xx -DSTM32_THREAD_SAFE_STRATEGY=4 -c -I../Core/Inc -I"C:/Users/Workstation/Documents/GitHub/MDP/STM32 Code/STM32_code/Core/ICM20948_lib" -I../Drivers/STM32F4xx_HAL_Driver/Inc -I../Drivers/STM32F4xx_HAL_Driver/Inc/Legacy -I../Drivers/CMSIS/Device/ST/STM32F4xx/Include -I../Drivers/CMSIS/Include -I../Middlewares/Third_Party/FreeRTOS/Source/include -I../Middlewares/Third_Party/FreeRTOS/Source/CMSIS_RTOS_V2 -I../Middlewares/Third_Party/FreeRTOS/Source/portable/GCC/ARM_CM4F -I../Core/ThreadSafe -I"C:/Users/Workstation/Documents/GitHub/MDP/STM32 Code/STM32_code/Core/ICM20948" -O0 -ffunction-sections -fdata-sections -fno-exceptions -fno-rtti -fno-use-cxa-atexit -Wall -fstack-usage -MMD -MP -MF"$(@:%.o=%.d)" -MT"$@" --specs=nano.specs -mfpu=fpv4-sp-d16 -mfloat-abi=hard -mthumb -o "$@"

clean: clean-Core-2f-ICM20948

clean-Core-2f-ICM20948:
	-$(RM) ./Core/ICM20948/ICM_20948.d ./Core/ICM20948/ICM_20948.o ./Core/ICM20948/ICM_20948.su

.PHONY: clean-Core-2f-ICM20948

