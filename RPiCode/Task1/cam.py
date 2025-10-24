#!/usr/bin/env python3
from picamera import PiCamera
import io
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


url = "http://192.168.39.18:5000/image"

cam = PiCamera()

cam.resolution = (1920, 1080)
cam.brightness = 70
cam.iso = 500

filename = "1.jpg"

cam.contrast = 50
cam.saturation = 0
cam.sharpness = 10

cam.start_preview()
time.sleep(0.4)
cam.capture(filename)

with open(filename, "rb") as f:
    resp = requests.post(url, files={"file": (filename, f)}, timeout=6)



