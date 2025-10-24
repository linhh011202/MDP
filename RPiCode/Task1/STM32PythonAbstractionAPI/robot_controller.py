from asyncio import Task
from dataclasses import dataclass
from enum import Enum
from typing import Optional, Callable, Any
import serial as ser
import time
from STM32PythonAbstractionAPI.stm32serial.serial_cmd_base_ll import SerialCmdBaseLL

# [WINDOWS COMPATIBILITY] - Removed RPi.GPIO import

'''
Python API that abstracts away the low-level serial communication with the robot.
This version is modified to run on Windows by removing Raspberry Pi-specific GPIO dependencies.
'''

class PinState(Enum):
    HIGH = 1
    LOW = 0
    Z = -1

class RobotController:
    drv = None  # instance of serial_cmd_base_ll
    _inst_obstr_cb = None  # obstacle callback

    # [WINDOWS COMPATIBILITY] - GPIO Pin definitions are no longer used but kept for reference
    PIN_COMMAND = 15
    PIN_OBSTACLE = 14

    def __init__(self, port, baudrate, _inst_obstr_cb: Optional[Callable[..., None]] = None):
        """
        Initializes the Robot Controller.
        port: The COM port on Windows (e.g., 'COM3').
        baudrate: The serial baud rate (e.g., 115200).
        """
        self.drv = SerialCmdBaseLL(port, baudrate)
        print("[CONTROLLER] Initialized for Windows.")
        print("[CONTROLLER] WARN: GPIO functionality (poll_is_moving, poll_obstruction, obstacle callback) is disabled.")
        
        # [WINDOWS COMPATIBILITY] - All GPIO setup and event detection code has been removed.
        self._inst_obstr_cb = _inst_obstr_cb
        if self._inst_obstr_cb is not None:
            print("[CONTROLLER] WARN: Obstacle callback was provided but cannot be used without GPIO.")


    '''
    Command robot to move forward/backward by [dist] cm.
    0 <= dist <= 999
    '''
    def move_forward(self, dist: int, no_brakes: bool = False) -> bool:
        if dist < 0 or dist > 999:
            raise ValueError("Invalid distance, must be 0-999")

        self.drv.construct_cmd()
        self.drv.add_cmd_byte(True)
        self.drv.add_module_byte(self.drv.Modules.MOTOR)
        self.drv.add_motor_cmd_byte(self.drv.MotorCmd.FWD_CHAR)
        self.drv.add_args_bytes(dist)
        # In your original code, this was padded, but for forward/backward,
        # the extra options should come after the argument.
        if no_brakes:
            self.drv.add_motor_cmd_byte(self.drv.MotorCmd.LINEAR_CHAR)
        self.drv.pad_to_end()
        return self.drv.ll_is_valid(self.drv.send_cmd())

    def move_backward(self, dist: int, no_brakes: bool = False) -> bool:
        if dist < 0 or dist > 999:
            raise ValueError("Invalid distance, must be 0-999")

        self.drv.construct_cmd()
        self.drv.add_cmd_byte(True)
        self.drv.add_module_byte(self.drv.Modules.MOTOR)
        self.drv.add_motor_cmd_byte(self.drv.MotorCmd.BWD_CHAR)
        self.drv.add_args_bytes(dist)
        if no_brakes:
            self.drv.add_motor_cmd_byte(self.drv.MotorCmd.LINEAR_CHAR)
        self.drv.pad_to_end()
        return self.drv.ll_is_valid(self.drv.send_cmd())

    '''
    Command robot to turn left/right by [angle] degrees.
    dir = True means turn forward, dir = False means turn backward.
    '''
    def turn_left(self, angle: int, dir: bool, no_brakes: bool = False) -> bool:
        if angle < 0 or angle > 359:
            raise ValueError("Invalid angle, must be 0-359")

        self.drv.construct_cmd()
        self.drv.add_cmd_byte(True)
        self.drv.add_module_byte(self.drv.Modules.MOTOR)
        self.drv.add_motor_cmd_byte(self.drv.MotorCmd.LEFT_CHAR)
        self.drv.add_args_bytes(angle)
        if dir:
            self.drv.add_motor_cmd_byte(self.drv.MotorCmd.FWD_CHAR)
        else:
            self.drv.add_motor_cmd_byte(self.drv.MotorCmd.BWD_CHAR)
        if no_brakes:
            self.drv.add_motor_cmd_byte(self.drv.MotorCmd.LINEAR_CHAR)
        self.drv.pad_to_end()
        return self.drv.ll_is_valid(self.drv.send_cmd())

    def turn_right(self, angle: int, dir: bool, no_brakes: bool = False) -> bool:
        if angle < 0 or angle > 359:
            raise ValueError("Invalid angle, must be 0-359")

        self.drv.construct_cmd()
        self.drv.add_cmd_byte(True)
        self.drv.add_module_byte(self.drv.Modules.MOTOR)
        self.drv.add_motor_cmd_byte(self.drv.MotorCmd.RIGHT_CHAR)
        self.drv.add_args_bytes(angle)
        if dir:
            self.drv.add_motor_cmd_byte(self.drv.MotorCmd.FWD_CHAR)
        else:
            self.drv.add_motor_cmd_byte(self.drv.MotorCmd.BWD_CHAR)
        if no_brakes:
            self.drv.add_motor_cmd_byte(self.drv.MotorCmd.LINEAR_CHAR)
        self.drv.pad_to_end()
        return self.drv.ll_is_valid(self.drv.send_cmd())

    # --- Sensor Reading Functions (Unchanged, work over serial) ---
    def get_quaternion(self) -> Optional[list]:
        # ... (implementation is unchanged)
        pass

    def get_gyro_Z(self) -> Optional[float]:
        # ... (implementation is unchanged)
        pass
    
    def get_yaw(self) -> Optional[float]:
        self.drv.construct_cmd()
        self.drv.add_cmd_byte(False)
        self.drv.add_module_byte(self.drv.Modules.SENSOR)
        self.drv.add_sensor_byte(self.drv.SensorCmd.QTRN_YAW)
        self.drv.pad_to_end()
        ret = self.drv.send_cmd()
        try:
            ret = float(ret)
        except (ValueError, TypeError):
            return None
        return ret
    
    # ... (all other get_* and set_* methods are unchanged) ...

    # --- GPIO Functions (Modified for Windows) ---
    
    def sig_obst_callback(self, channel):
        # [WINDOWS COMPATIBILITY] - This function will never be called.
        print("[CONTROLLER] WARN: sig_obst_callback was triggered, which should not happen on Windows.")
        if self._inst_obstr_cb is not None:
            _d_ret = self.get_last_successful_arg()
            self._inst_obstr_cb(_d_ret)

    def poll_obstruction(self):
        # [WINDOWS COMPATIBILITY] - GPIO polling is not possible. Returns a default value.
        print("[CONTROLLER] WARN: poll_obstruction() is not supported on Windows. Returning 0.")
        return 0 # Return 0 (False) as a safe default

    def poll_is_moving(self):
        # [WINDOWS COMPATIBILITY] - GPIO polling is not possible. Returns a default value.
        print("[CONTROLLER] WARN: poll_is_moving() is not supported on Windows. Returning 0.")
        return 0 # Return 0 (False) as a safe default

    def halt(self):
        self.drv.construct_cmd()
        self.drv.add_cmd_byte(True)
        self.drv.add_module_byte(self.drv.Modules.MOTOR)
        self.drv.add_motor_cmd_byte(self.drv.MotorCmd.HALT_CHAR) # Corrected from add_sensor_byte
        self.drv.pad_to_end()
        return self.drv.ll_is_valid(self.drv.send_cmd())
    

    # In the RobotController class...

    def is_busy(self) -> Optional[bool]:
        """Requests the robot's busy status."""
        self.drv.construct_cmd()
        self.drv.add_cmd_byte(False) # This is a request (q)
        self.drv.add_module_byte(self.drv.Modules.SENSOR) # We'll put this under 'sensor'
        
        # In serial_cmd_base_ll.py, add: IS_BUSY = b'b' to SensorCmd enum
        # In robot_controller.py, inside the is_busy method
        self.drv.add_sensor_byte(SerialCmdBaseLL.SensorCmd.IS_BUSY)
        
        self.drv.pad_to_end()
        ret = self.drv.send_cmd()
        if '1' in ret:
            return True
        if '0' in ret:
            return False
        return None # In case of bad response

    def wait_until_idle(self, timeout_s: float = 60.0):
        """Polls the robot until it is no longer busy, or until a timeout occurs."""
        print("Waiting for robot to become idle...")
        start_time = time.time()
        while time.time() - start_time < timeout_s:
            if not self.is_busy():
                print("Robot is idle.")
                return True
            time.sleep(0.1) # Poll 10 times per second
        print("Wait timed out!")
        return False 

    # ... (crawl_forward, crawl_backward, get_last_successful_arg, T2_* methods are unchanged) ...
