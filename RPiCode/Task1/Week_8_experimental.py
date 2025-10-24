
#!/usr/bin/env python3
import json
import queue
import time
from multiprocessing import Process, Manager
from typing import Optional
import os
import requests
from communication.android import AndroidLink, AndroidMessage
from communication.stm32 import STMLink
from consts import SYMBOL_MAP
from logger import prepare_logger
from settings import API_IP, API_PORT
from STM32PythonAbstractionAPI.robot_controller import RobotController
from STM32PythonAbstractionAPI.dispatcher import BlockingDispatcher, _IO_Attr_Type
import asyncio


robot = RobotController("/dev/ttyACM0", 115200)
dispatcher = BlockingDispatcher(robot, 5, 2, u_if=_IO_Attr_Type.SIMULATED)

def handle_keyboard_input():
        print("Keyboard handler thread started.")
        
        while True:
            user_input = input("shortcut or manual: ").strip().lower()
            exitflag = False
            while exitflag == False:
                user_input = input("fw, bw, bl, br, 180, 360, exit: ").strip().lower()
                if user_input[0:2] == "fw":
                    if user_input == "fw":
                        print("Moving standard 30cm")
                        robot.move_forward(30)
                    elif user_input[2:].isdigit():
                        robot.move_forward(int(user_input[2:]))
                elif user_input[0:2] == "bw":
                    if user_input == "bw":
                        print("Moving standard 30cm")
                        robot.move_backward(30)
                    elif user_input[2:].isdigit():
                        robot.move_backward(int(user_input[2:]))
                elif user_input[0:2] == "bl":
                    if user_input == "bl":
                        robot.turn_right(90,1)
                    elif user_input[2:].isdigit():
                        robot.turn_right(int(user_input[2:]),1)
                elif user_input[0:2] == "br":
                    if user_input == "br":
                        robot.turn_left(90,1)
                    if user_input[2:].isdigit():
                        robot.turn_left(int(user_input[2:]),1)

                elif user_input == "180":
                    robot.turn_left(180, 1)
                elif user_input == "360":
                    robot.turn_left(359, 1)
                elif user_input == "exit":
                    exitflag = True
                else:
                    print("Invalid manual command") 
        else:
            print("Unknown keyboard command")

handle_keyboard_input()