#!/usr/bin/env python3
import random
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
from logger import prepare_logger
from settings import API_IP, API_PORT
from stm32_api.robot_controller import RobotController
import asyncio

robot = RobotController("/dev/ttyACM0", 115200)


def capture_and_classify_left_right():
    """
    Take a photo with PiCamera, POST to the /image endpoint, and print LEFT/RIGHT
    if recognized. Ignores other classes.
    """
    import os, time, json, requests
    try:
        from picamera import PiCamera
    except Exception as e:
        print(e)
        return

    # Resolve API host/port if you have settings.py; else use env or default localhost:5000
    try:
        from settings import PC as _API_IP, FLASK_PORT as _API_PORT
    except Exception:
        _API_IP = os.environ.get("API_IP", "192.168.39.18")
        _API_PORT = os.environ.get("API_PORT", "5000")

    url = f"http://{_API_IP}:{_API_PORT}/image"
    base_name = f"{int(time.time())}_0_X"  # obstacle/signal not needed for week 9 server
    max_retries = 6
    result = {"image_id": "NA"}

    cam = None
    try:
        cam = PiCamera()
        cam.resolution = (600, 680)

        for retry in range(1, max_retries + 1):
            # Simple exposure strategy (ported from your second code, shortened)
            if retry == 1:
                cam.brightness = 60
                cam.iso = 600
            elif retry <= 3:
                cam.brightness = min(70 + retry * 5, 100)
                cam.iso = min(600 + retry * 100, 800)
            else:
                cam.brightness = max(50 - (retry - 3) * 5, 30)
                cam.iso = max(600 - (retry - 3) * 100, 400)
            cam.contrast = 50
            cam.saturation = 0
            cam.sharpness = 0

            if retry == 1:
                try:
                    cam.start_preview()
                except Exception:
                    pass
                time.sleep(0.0)  # initial warm-up
            else:
                time.sleep(0.4)

            filename = f"{base_name}_r{retry}.jpg"
            try:
                cam.capture(filename)
            except Exception as e:
                print(f"[IMG] Capture error (attempt {retry}): {e}")
                continue

            # Send to server
            try:
                with open(filename, "rb") as f:
                    resp = requests.post(url, files={"file": (filename, f)}, timeout=6)
            except Exception as e:
                print(f"[IMG] HTTP error (attempt {retry}): {e}")
                continue

            if resp.status_code != 200:
                print(f"[IMG] API {resp.status_code}: {resp.text}")
                continue

            try:
                result = json.loads(resp.content)
            except Exception as e:
                print(f"[IMG] JSON decode error: {e}")
                continue

            # Stop retrying if we got a non-NA prediction
            if result.get("image_id") != "NA":
                break

    finally:
        if cam is not None:
            try:
                cam.stop_preview()
            except Exception:
                pass
            cam.close()

    image_id = str(result.get("image_id", "NA"))
    confidence = float(result.get("score",-0.001))
    print(image_id,confidence)
    if image_id == "22" and confidence > 0.3:
        print("Returning 0")
        return(0)
    if image_id == "25" and confidence > 0.3:
        print("Returning 1")
        return(1)
    # else: silently ignore other/unknown classes as requested
    return(None)


async def main():
    o1_dir = None
    o2_dir = None
    robot.move_forward_but_actually_move_to_o1(30)
    robot.wait_until_idle()
    o1_dir_tries = 0
    while o1_dir_tries < 10 and o1_dir == None:
        print("capture_and_classify called for o1")
        o1_dir = capture_and_classify_left_right()
        time.sleep(1)
        if o1_dir_tries == 9 and o1_dir == None:
            print("Failed to classify o1, coinflip")
            o1_dir = random.randint(0,1)
        o1_dir_tries += 1
    if o1_dir == 0:
        robot.turn_left_but_actually_dodge_o1_left(30,True)
    elif o1_dir == 1:
        robot.turn_right_but_actually_dodge_o1_right(30,True)
    robot.wait_until_idle()
    o2_dir_tries = 0
    while o2_dir_tries < 10 and o2_dir == None:
        print("capture_and_classify called for o2")
        o2_dir = capture_and_classify_left_right()
        time.sleep(1)
        if o2_dir_tries == 9 and o2_dir == None:
            print("Failed to classify o2, coinflip")
            o2_dir = random.randint(0,1)
        o2_dir_tries += 1
    if o2_dir == 0:
        robot.move_backward_but_actually_zero_o2_left_one_o2_right(0)
    elif o2_dir == 1:
            robot.move_backward_but_actually_zero_o2_left_one_o2_right(1)


if __name__ == '__main__':
    from Connection.RPI_comms import RPI_connection
    rpi = RPI_connection()
    rpi.bluetooth_connect()
    while True:
      print("Robot ready. Press start on android.")
      if "start" == rpi.android_receive():
          break
    asyncio.run(main())  # still commented out as in your original
    rpi.bluetooth_disconnect()


                    
    

