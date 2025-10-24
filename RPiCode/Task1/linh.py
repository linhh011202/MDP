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

robot = RobotController("/dev/ttyACM0", 115200)
dispatcher = BlockingDispatcher(robot, 5, 2, u_if=_IO_Attr_Type.SIMULATED)

async def turn_error_minimization(phi: float, offset_known: bool, phi_cur: float, sgn_offset: float = 0):
    if offset_known:

        if (phi + sgn_offset) >= 180:
            phi_opt = -180 + ((phi + sgn_offset) % 180)
        elif (phi + sgn_offset) < -180:
            phi_opt = ((phi + sgn_offset)) % 180
        else:
            phi_opt = (phi + sgn_offset)

        epsl = phi_opt - phi_cur
        if abs(epsl) <= 180:
            return abs(epsl), 1 if epsl > 0 else 0
        else:
            return 360 - epsl, 1 if epsl <= 0 else 0

    else:
        epsl_vars = []
        # basically repeat the above for all k in -2..2
        _CONST_ROT_MAG = 90
        for k in (-2, 3):
            if (phi + _CONST_ROT_MAG * k) >= 180:
                phi_opt = -180 + ((phi + _CONST_ROT_MAG * k) % 180)
            elif (phi + _CONST_ROT_MAG * k) < -180:
                phi_opt = (phi + _CONST_ROT_MAG * k) % 180
            else:
                phi_opt = (phi + _CONST_ROT_MAG * k)

            epsl = phi_opt - phi_cur
            if abs(epsl) <= 180:
                epsl_vars.append([abs(epsl), 1 if epsl > 0 else 0])
            else:
                epsl_vars.append([360 - epsl, 1 if epsl <= 0 else 0])
        n = 0
        for k, _ in enumerate(epsl_vars):
            if epsl_vars[k][0] < epsl_vars[n][0]:
                n = k
        return epsl_vars[n]

def translate_and_dispatch(cmds, on_fin=None):
    """
    Execute movement & SNAP commands directly (no command_queue).
    Supported formats:
      Movement: FW<n>, FS<n>, BW<n>, BS<n>, FL, FR, BL, BR
      SNAP: SNAP<obstacleId>_<signal>   e.g. SNAP3_A  or SNAP12_C
      FIN: finish sequence
    """
    def wait_until_idle():
        # Poll robot until movement completes (synchronous sequencing)
        while True:
            try:
                if not robot.wait_until_idle():
                    break
            except Exception:
                break
            time.sleep(0.05)

    def do_snap(snap_token: str):
        """
        snap_token example: '3_A' or '12_C'
        Captures image with PiCamera and posts to image API with retry exposure strategy.
        """
        try:
            obstacle_id, signal = snap_token.split("_", 1)
        except ValueError:
            print(f"[SNAP] Invalid SNAP token: {snap_token}")
            return

        url = f"http://{API_IP}:{API_PORT}/image"
        base_name = f"{int(time.time())}_{obstacle_id}_{signal}"
        max_retries = 6
        retry = 0
        result = {"image_id": "NA", "obstacle_id": obstacle_id}

        print(f"[SNAP] Capturing obstacle {obstacle_id} signal {signal}")

        cam = None
        try:
            cam = PiCamera()
            cam.resolution = (1920, 1080)

            while retry < max_retries:
                retry += 1
                # Adaptive brightness / ISO
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
                    cam.start_preview()
                    time.sleep(2)
                else:
                    time.sleep(0.4)

                filename = f"{base_name}_r{retry}.jpg"
                print(f"[SNAP] Attempt {retry}: capturing {filename}")
                try:
                    cam.capture(filename)
                except Exception as e:
                    print(f"[SNAP] Capture error: {e}")
                    continue

                # Send to API
                try:
                    with open(filename, "rb") as f:
                        resp = requests.post(url, files={"file": (filename, f)}, timeout=6)
                except Exception as e:
                    print(f"[SNAP] HTTP error: {e}")
                    continue

                if resp.status_code != 200:
                    print(f"[SNAP] API status {resp.status_code}: {resp.text}")
                    continue

                try:
                    result = json.loads(resp.content)
                except Exception as e:
                    print(f"[SNAP] JSON decode error: {e}")
                    continue

                print(f"[SNAP] Result attempt {retry}: {result}")
                if result.get("image_id") != "NA":
                    break

            symbol_readable = SYMBOL_MAP.get(result.get("image_id"), "Unknown")
            print(f"[SNAP] Final recognition: {result} -> {symbol_readable}")

        finally:
            if cam:
                try:
                    cam.stop_preview()
                except:
                    pass
                cam.close()

    for cmd in cmds:
        cmd = cmd.strip()
        if not cmd:
            continue

        # SNAP
        if cmd.startswith("SNAP"):
            # Accept formats: SNAP3_A / SNAP12_C / SNAP5_X
            snap_payload = cmd[4:]  # remove 'SNAP'
            if snap_payload.startswith("_"):
                snap_payload = snap_payload[1:]

            robot.wait_until_idle()
            do_snap(snap_payload)
            time.sleep(1)
            continue

        # FIN
        if cmd == "FIN":
            print("[FIN] Sequence complete.")
            if on_fin:
                try:
                    on_fin()
                except Exception as e:
                    print(f"[FIN] on_fin handler error: {e}")
            time.sleep(0.5)
            return
 
        # Movement parsing
        try:
            if cmd.startswith(("FW", "FS")):      # forward
                dist = int(cmd[2:])
                robot.move_forward(dist)
                time.sleep(0.8)
                robot.wait_until_idle()
            elif cmd.startswith(("BW", "BS")):    # backward
                dist = int(cmd[2:])
                robot.move_backward(dist)
                time.sleep(0.8)
                robot.wait_until_idle()
            elif cmd[:2] == "FL":
                #new code:
                robot.move_forward(20)
                time.sleep(2)
                robot.turn_left(90, 1)
                time.sleep(0.8)
                robot.wait_until_idle()
            elif cmd[:2] == "FR":
                robot.move_forward(20)
                time.sleep(2)
                robot.turn_right(90, 1)
                time.sleep(0.8)
                robot.wait_until_idle()
            elif cmd[:2] == "BL":
                #robot.move_forward(10)
                time.sleep(0.8)
                robot.turn_right(90, 0)
                time.sleep(0.8)
                robot.wait_until_idle()
            elif cmd[:2] == "BR":
                #robot.move_forward(5)
                time.sleep(0.8)
                robot.turn_left(90, 0)
                time.sleep(0.8)
                robot.wait_until_idle()
            else:
                print(f"[WARN] Unknown command ignored: {cmd}")
        except Exception as e:
            print(f"[ERR] Executing {cmd} failed: {e}")

    return None


class PiAction:
    """
    Class that represents an action that the RPi needs to take.    
    """

    def __init__(self, cat, value):
        """
        :param cat: The category of the action. Can be 'info', 'mode', 'path', 'snap', 'obstacle', 'location', 'failed', 'success'
        :param value: The value of the action. Can be a string, a list of coordinates, or a list of obstacles.
        """
        self._cat = cat
        self._value = value

    @property
    def cat(self):
        return self._cat

    @property
    def value(self):
        return self._value


class RaspberryPi:
    """
    Class that represents the Raspberry Pi.
    """

    def __init__(self):
        """
        Initializes the Raspberry Pi.
        """
        self.logger = prepare_logger()
        self.android_link = AndroidLink()
        self.stm_link = STMLink()

        self.manager = Manager()

        self.android_dropped = self.manager.Event()
        self.unpause = self.manager.Event()

        self.movement_lock = self.manager.Lock()

        self.android_queue = self.manager.Queue()  # Messages to send to Android
        # Messages that need to be processed by RPi
        self.rpi_action_queue = self.manager.Queue()
        # Messages that need to be processed by STM32, as well as snap commands
        self.command_queue = self.manager.Queue()
        # X,Y,D coordinates of the robot after execution of a command
        self.path_queue = self.manager.Queue()

        self.proc_recv_android = None
        self.proc_recv_stm32 = None
        self.proc_android_sender = None
        self.proc_command_follower = None
        self.proc_rpi_action = None
        self.rs_flag = False
        self.success_obstacles = self.manager.list()
        self.failed_obstacles = self.manager.list()
        self.obstacles = self.manager.dict()
        self.current_location = self.manager.dict()
        self.failed_attempt = False

    def start(self):
        """Starts the RPi orchestrator"""
        try:
            ### Start up initialization ###

            self.android_link.connect()
            self.android_queue.put(AndroidMessage(
                'info', 'You are connected to the RPi!'))
            ####self.stm_link.connect()
            self.check_api()

            # Define child processes
            self.proc_recv_android = Process(target=self.recv_android)
            ####self.proc_recv_stm32 = Process(target=self.recv_stm)
            self.proc_android_sender = Process(target=self.android_sender)
            self.proc_command_follower = Process(target=self.command_follower)
            self.proc_rpi_action = Process(target=self.rpi_action)

            # Start child processes
            self.proc_recv_android.start()
            ####self.proc_recv_stm32.start()
            self.proc_android_sender.start()
            self.proc_command_follower.start()
            self.proc_rpi_action.start()

            self.logger.info("Child Processes started")

            ### Start up complete ###

            # Send success message to Android
            self.android_queue.put(AndroidMessage('info', 'Robot is ready!'))
            self.android_queue.put(AndroidMessage('mode', 'path'))
            self.reconnect_android()

        except KeyboardInterrupt:
            self.stop()

    def stop(self):
        """Stops all processes on the RPi and disconnects gracefully with Android and STM32"""
        self.android_link.disconnect()
        self.stm_link.disconnect()
        self.logger.info("Program exited!")

    def reconnect_android(self):
        """Handles the reconnection to Android in the event of a lost connection."""
        self.logger.info("Reconnection handler is watching...")

        while True:
            # Wait for android connection to drop
            self.android_dropped.wait()

            self.logger.error("Android link is down!")

            # Kill child processes
            self.logger.debug("Killing android child processes")
            self.proc_android_sender.kill()
            self.proc_recv_android.kill()

            # Wait for the child processes to finish
            self.proc_android_sender.join()
            self.proc_recv_android.join()
            assert self.proc_android_sender.is_alive() is False
            assert self.proc_recv_android.is_alive() is False
            self.logger.debug("Android child processes killed")

            # Clean up old sockets
            self.android_link.disconnect()

            # Reconnect
            self.android_link.connect()

            # Recreate Android processes
            self.proc_recv_android = Process(target=self.recv_android)
            self.proc_android_sender = Process(target=self.android_sender)

            # Start previously killed processes
            self.proc_recv_android.start()
            self.proc_android_sender.start()

            self.logger.info("Android child processes restarted")
            self.android_queue.put(AndroidMessage(
                "info", "You are reconnected!"))
            self.android_queue.put(AndroidMessage('mode', 'path'))

            self.android_dropped.clear()

    def recv_android(self) -> None:
        """
        [Child Process] Processes the messages received from Android
        """
        while True:
            msg_str: Optional[str] = None
            try:
                msg_str = self.android_link.recv()
            except OSError:
                self.android_dropped.set()
                self.logger.debug("Event set: Android connection dropped")

            if msg_str is None:
                continue

            message: dict = json.loads(msg_str)

            ## Command: Set obstacles ##
            if message['cat'] == "obstacles":
                self.rpi_action_queue.put(PiAction(**message))
                self.logger.debug(
                    f"Set obstacles PiAction added to queue: {message}")

            ## Command: Start Moving ##
            elif message['cat'] == "control":
                if message['value'] == "start":
                    # Check API
                    if not self.check_api():
                        self.logger.error(
                            "API is down! Start command aborted.")
                        self.android_queue.put(AndroidMessage(
                            'error', "API is down, start command aborted."))

                    # Commencing path following
                    if not self.command_queue.empty():
                        self.logger.info("Gryo reset!")
                        self.stm_link.send("RS00")
                        # Main trigger to start movement #
                        self.unpause.set()
                        self.logger.info(
                            "Start command received, starting robot on path!")
                        self.android_queue.put(AndroidMessage(
                            'info', 'Starting robot on path!'))
                        self.android_queue.put(
                            AndroidMessage('status', 'running'))
                    else:
                        self.logger.warning(
                            "The command queue is empty, please set obstacles.")
                        self.android_queue.put(AndroidMessage(
                            "error", "Command queue is empty, did you set obstacles?"))

    def recv_stm(self) -> None:
        """
        [Child Process] Receive acknowledgement messages from STM32, and release the movement lock
        """
        while True:

            message: str = self.stm_link.recv()

            if message.startswith("ACK"):
                if self.rs_flag == False:
                    self.rs_flag = True
                    self.logger.debug("ACK for RS00 from STM32 received.")
                    continue
                try:
                    self.movement_lock.release()
                    try:
                        self.retrylock.release()
                    except:
                        pass
                    self.logger.debug(
                        "ACK from STM32 received, movement lock released.")

                    cur_location = self.path_queue.get_nowait()

                    self.current_location['x'] = cur_location['x']
                    self.current_location['y'] = cur_location['y']
                    self.current_location['d'] = cur_location['d']
                    self.logger.info(
                        f"self.current_location = {self.current_location}")
                    self.android_queue.put(AndroidMessage('location', {
                        "x": cur_location['x'],
                        "y": cur_location['y'],
                        "d": cur_location['d'],
                    }))

                except Exception:
                    self.logger.warning("Tried to release a released lock!")
            else:
                self.logger.warning(
                    f"Ignored unknown message from STM: {message}")

    def android_sender(self) -> None:
        """
        [Child process] Responsible for retrieving messages from android_queue and sending them over the Android link. 
        """
        while True:
            # Retrieve from queue
            try:
                message: AndroidMessage = self.android_queue.get(timeout=0.5)
            except queue.Empty:
                continue

            try:
                self.android_link.send(message)
            except OSError:
                self.android_dropped.set()
                self.logger.debug("Event set: Android dropped")

    def command_follower(self) -> None:
        """
        [Child Process] 
        """
        while True:
            # Retrieve next movement command
            command: str = self.command_queue.get()
            self.logger.debug("wait for unpause")
            # Wait for unpause event to be true [Main Trigger]
            try:
                self.logger.debug("wait for retrylock")
                self.retrylock.acquire()
                self.retrylock.release()
            except:
                self.logger.debug("wait for unpause")
                self.unpause.wait()
            self.logger.debug("wait for movelock")
            # Acquire lock first (needed for both moving, and snapping pictures)
            self.movement_lock.acquire()

            # STM32 Commands - Send straight to STM32
            stm32_prefixes = ("FS", "BS", "FW", "BW", "FL", "FR", "BL",
                              "BR", "TL", "TR", "A", "C", "DT", "STOP", "ZZ", "RS")
            if command.startswith(stm32_prefixes):
                self.stm_link.send(command)
                self.logger.debug(f"Sending to STM32: {command}")

            # Snap command
            elif command.startswith("SNAP"):
                obstacle_id_with_signal = command.replace("SNAP", "")
                self.rpi_action_queue.put(
                    PiAction(cat="snap", value=obstacle_id_with_signal))

            # End of path
            elif command == "FIN":
                self.logger.info(
                    f"At FIN, self.failed_obstacles: {self.failed_obstacles}")
                self.logger.info(
                    f"At FIN, self.current_location: {self.current_location}")
                if len(self.failed_obstacles) != 0 and self.failed_attempt == False:

                    new_obstacle_list = list(self.failed_obstacles)
                    for i in list(self.success_obstacles):
                        # {'x': 5, 'y': 11, 'id': 1, 'd': 4}
                        i['d'] = 8
                        new_obstacle_list.append(i)

                    self.logger.info("Attempting to go to failed obstacles")
                    self.failed_attempt = True
                    self.request_algo({'obstacles': new_obstacle_list, 'mode': '0'},
                                      self.current_location['x'], self.current_location['y'], self.current_location['d'], retrying=True)
                    self.retrylock = self.manager.Lock()
                    self.movement_lock.release()
                    continue

                self.unpause.clear()
                self.movement_lock.release()
                self.logger.info("Commands queue finished.")
                self.android_queue.put(AndroidMessage(
                    "info", "Commands queue finished."))
                self.android_queue.put(AndroidMessage("status", "finished"))
                self.rpi_action_queue.put(PiAction(cat="stitch", value=""))
            else:
                raise Exception(f"Unknown command: {command}")

    def rpi_action(self):
        """
        [Child Process] 
        """
        while True:
            action: PiAction = self.rpi_action_queue.get()
            self.logger.debug(
                f"PiAction retrieved from queue: {action.cat} {action.value}")

            if action.cat == "obstacles":
                for obs in action.value['obstacles']:
                    self.obstacles[obs['id']] = obs
                self.request_algo(action.value)
            elif action.cat == "snap":
                self.snap_and_rec(obstacle_id_with_signal=action.value)
            elif action.cat == "stitch":
                self.request_stitch()

    def snap_and_rec(self, obstacle_id_with_signal: str) -> None:
        """
        RPi snaps an image and calls the API for image-rec.
        The response is then forwarded back to the android
        :param obstacle_id_with_signal: the current obstacle ID followed by underscore followed by signal
        """
        from picamera import PiCamera
        import io
        
        obstacle_id, signal = obstacle_id_with_signal.split("_")
        self.logger.info(f"Capturing image for obstacle id: {obstacle_id}")
        self.android_queue.put(AndroidMessage(
            "info", f"Capturing image for obstacle id: {obstacle_id}"))
        url = f"http://{API_IP}:{API_PORT}/image"
        filename = f"{int(time.time())}_{obstacle_id}_{signal}.jpg"

        retry_count = 0
        camera = None

        try:
            # Initialize camera once
            camera = PiCamera()
            camera.resolution = (1920, 1080)  # HD resolution
            
            while True:
                retry_count += 1
                
                try:
                    # Adjust camera settings based on retry_count
                    if retry_count == 1:
                        # Default settings
                        camera.brightness = 70
                        camera.contrast = 50
                        camera.iso = 600
                    elif retry_count <= 3:
                        # Higher brightness for initial retries
                        camera.brightness = 75
                    else:
                        # Lower brightness for later retries
                        camera.brightness = 65
                    
                    camera.saturation = 0
                    camera.sharpness = 0
                    
                    # Give camera time to adjust settings
                    if retry_count == 1:
                        camera.start_preview()
                        time.sleep(1)  # Initial warm-up
                    else:
                        time.sleep(0.5)  # Brief adjustment time
                    
                    self.logger.info(f"Capturing image (attempt {retry_count}): {filename}")
                    
                    # Capture image
                    camera.capture(filename)
                    
                    self.logger.debug("Requesting from image API")
                    
                    # Send to API
                    with open(filename, 'rb') as img_file:
                        response = requests.post(
                            url, files={"file": (filename, img_file)})
                    
                    if response.status_code != 200:
                        self.logger.error(
                            "Something went wrong when requesting path from image-rec API. Please try again.")
                        return
                    
                    results = json.loads(response.content)
                    
                    # Check if recognition was successful
                    if results['image_id'] != 'NA' or retry_count > 6:
                        break
                    elif retry_count > 3:
                        self.logger.info(f"Image recognition results: {results}")
                        self.logger.info("Recapturing with lower brightness...")
                    elif retry_count <= 3:
                        self.logger.info(f"Image recognition results: {results}")
                        self.logger.info("Recapturing with higher brightness...")
                        
                except Exception as e:
                    self.logger.error(f"Error during image capture/processing: {e}")
                    if retry_count > 6:
                        break
                    continue
            
        finally:
            # Clean up camera
            if camera is not None:
                try:
                    camera.stop_preview()
                    camera.close()
                except:
                    pass

        # release lock so that bot can continue moving
        self.movement_lock.release()
        try:
            self.retrylock.release()
        except:
            pass

        self.logger.info(f"results: {results}")
        self.logger.info(f"self.obstacles: {self.obstacles}")
        self.logger.info(
            f"Image recognition results: {results} ({SYMBOL_MAP.get(results['image_id'])})")

        if results['image_id'] == 'NA':
            self.failed_obstacles.append(
                self.obstacles[int(results['obstacle_id'])])
            self.logger.info(
                f"Added Obstacle {results['obstacle_id']} to failed obstacles.")
            self.logger.info(f"self.failed_obstacles: {self.failed_obstacles}")
        else:
            self.success_obstacles.append(
                self.obstacles[int(results['obstacle_id'])])
            self.logger.info(
                f"self.success_obstacles: {self.success_obstacles}")
        self.android_queue.put(AndroidMessage("image-rec", results))

    def request_algo(self, data, robot_x=1, robot_y=1, robot_dir=0, retrying=False):
        """
        Requests for a series of commands and the path from the Algo API.
        The received commands and path are then queued in the respective queues
        """
        self.logger.info("Requesting path from algo...")
        self.android_queue.put(AndroidMessage(
            "info", "Requesting path from algo..."))
        self.logger.info(f"data: {data}")
        body = {**data, "big_turn": "0", "robot_x": robot_x,
                "robot_y": robot_y, "robot_dir": robot_dir, "retrying": retrying}
        url = f"http://{API_IP}:{API_PORT}/path"
        response = requests.post(url, json=body)

        # Error encountered at the server, return early
        if response.status_code != 200:
            self.android_queue.put(AndroidMessage(
                "error", "Something went wrong when requesting path from Algo API."))
            self.logger.error(
                "Something went wrong when requesting path from Algo API.")
            return

        # Parse response
        result = json.loads(response.content)['data']
        commands = result['commands']
        path = result['path']

        # Log commands received
        self.logger.debug(f"Commands received from API: {commands}")
        translate_and_dispatch(commands, on_fin=lambda: self.rpi_action_queue.put(PiAction(cat="stitch", value="")))
        

        # # Put commands and paths into respective queues
        # self.clear_queues()
        # for c in commands:
        #     self.command_queue.put(c)
            
        # for p in path[1:]:  # ignore first element as it is the starting position of the robot
        #     self.path_queue.put(p)

        self.android_queue.put(AndroidMessage(
            "info", "Commands and path received Algo API. Robot is ready to move."))
        self.logger.info(
            "Commands and path received Algo API. Robot is ready to move.")

    def request_stitch(self):
        """Sends a stitch request to the image recognition API to stitch the different images together"""
        url = f"http://{API_IP}:{API_PORT}/stitch"
        response = requests.get(url)

        # If error, then log, and send error to Android
        if response.status_code != 200:
            # Notify android
            self.android_queue.put(AndroidMessage(
                "error", "Something went wrong when requesting stitch from the API."))
            self.logger.error(
                "Something went wrong when requesting stitch from the API.")
            return
        self.logger.info("Images stitched!")
        self.android_queue.put(AndroidMessage("info", "Images stitched!"))

    def clear_queues(self):
        """Clear both command and path queues"""
        while not self.command_queue.empty():
            self.command_queue.get()
        while not self.path_queue.empty():
            self.path_queue.get()

    def check_api(self) -> bool:
        """Check whether image recognition and algorithm API server is up and running

        Returns:
            bool: True if running, False if not.
        """
        # Check image recognition API
        url = f"http://{API_IP}:{API_PORT}/status"
        try:
            response = requests.get(url, timeout=1)
            if response.status_code == 200:
                self.logger.debug("API is up!")
                return True
            return False
        # If error, then log, and return False
        except ConnectionError:
            self.logger.warning("API Connection Error")
            return False
        except requests.Timeout:
            self.logger.warning("API Timeout")
            return False
        except Exception as e:
            self.logger.warning(f"API Exception: {e}")
            return False

    


if __name__ == '__main__':
    rpi = RaspberryPi()
    rpi.start()

