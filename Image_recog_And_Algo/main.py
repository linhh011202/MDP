import time
import os
import random
from PIL import Image
import cv2
import numpy as np
from algo.algo import MazeSolver 
from flask import Flask, request, jsonify
from flask_cors import CORS
from model import *
from model import predict_image_week_9
from helper import command_generator

"""
rm -rf runs/detect/*
rm -rf uploads/*
rm -rf own_results/*
"""

app = Flask(__name__)
CORS(app)
model = load_model()
#model = None

@app.route('/status', methods=['GET'])
def status():
    """
    This is a health check endpoint to check if the server is running
    :return: a json object with a key "result" and value "ok"
    """
    return jsonify({"result": "ok"})


@app.route('/path', methods=['POST'])
def path_finding():
    """
    This is the main endpoint for the path finding algorithm
    :return: a json object with a key "data" and value a dictionary with keys "distance", "path", and "commands"
    """
    # Get the json data from the request
    content = request.json

    # Get the obstacles, big_turn, retrying, robot_x, robot_y, and robot_direction from the json data
    obstacles = content['obstacles']
    # big_turn = int(content['big_turn'])
    retrying = content['retrying']
    robot_x, robot_y = content['robot_x'], content['robot_y']
    robot_direction = int(content['robot_dir'])

    # Initialize MazeSolver object with robot size of 20x20, bottom left corner of robot at (1,1), facing north, and whether to use a big turn or not.
    maze_solver = MazeSolver(20, 20, robot_x, robot_y, robot_direction, big_turn=None)

    # Add each obstacle into the MazeSolver. Each obstacle is defined by its x,y positions, its direction, and its id
    for ob in obstacles:
        maze_solver.add_obstacle(ob['x'], ob['y'], ob['d'], ob['id'])

    start = time.time()
    # Get shortest path
    optimal_path, distance = maze_solver.get_optimal_order_dp(retrying=retrying)
    print(f"Time taken to find shortest path using A* search: {time.time() - start}s")
    print(f"Distance to travel: {distance} units")
    
    # Based on the shortest path, generate commands for the robot
    commands = command_generator(optimal_path, obstacles)

    # Get the starting location and add it to path_results
    path_results = [optimal_path[0].get_dict()]
    # Process each command individually and append the location the robot should be after executing that command to path_results
    i = 0
    for command in commands:
        if command.startswith("SNAP"):
            continue
        if command.startswith("FIN"):
            continue
        elif command.startswith("FW") or command.startswith("FS"):
            i += int(command[2:]) // 10
        elif command.startswith("BW") or command.startswith("BS"):
            i += int(command[2:]) // 10
        else:
            i += 1
        path_results.append(optimal_path[i].get_dict())
    return jsonify({
        "data": {
            'distance': distance,
            'path': path_results,
            'commands': commands
        },
        "error": None
    })

def _adjust_brightness_contrast(pil_img, alpha, beta):
    # alpha: contrast (0.6-1.4), beta: brightness (-40..40)
    bgr = cv2.cvtColor(np.array(pil_img), cv2.COLOR_RGB2BGR)
    adj = cv2.convertScaleAbs(bgr, alpha=alpha, beta=beta)
    return Image.fromarray(cv2.cvtColor(adj, cv2.COLOR_BGR2RGB))
    

@app.route('/image', methods=['POST'])
def image_predict():
    """
    This is the main endpoint for the image prediction algorithm
    :return: a json object with a key "result" and value a dictionary with keys "obstacle_id" and "image_id"
    """
    file = request.files['file']
    filename = file.filename
    file.save(os.path.join('uploads', filename))

    # 1) Try original image
    image_id, score = predict_image_week_9(filename, model)

    # 2) If no detection, try up to 2!! random brightness/contrast variants
    if image_id == 'NA':
        base_img = Image.open(os.path.join('uploads', filename))
        for i in range(2):
            alpha = random.uniform(0.8, 1.2)   # contrast
            beta = random.randint(-20, 20)     # brightness
            aug_img = _adjust_brightness_contrast(base_img, alpha, beta)

            # Save to uploads/ and pass just the filename (what model.py expects)
            temp_name = f"aug_{i}_{filename}"
            temp_path = os.path.join('uploads', temp_name)
            try:
                aug_img.save(temp_path)
            except Exception:
                continue

            image_id, score = predict_image_week_9(temp_name, model)
            print(image_id, "Think BREAKING!!!\n")
            if image_id == '22' or image_id == '25':
                print(image_id, "I AM BREAKING NOW!!!\n")
                break
            # Clean up unsuccessful aug file
            try:
                os.remove(temp_path)
            except OSError:
                pass

    # 3) If still none, 50/50 return "22" or "25"
    if image_id == 'NA':
        image_id = random.choice(['22', '25'])
        score = 0.001

    result = {
        "image_id": image_id,
        "score": score
    }
    print(result)
    return jsonify(result)

@app.route('/stitch', methods=['GET'])
def stitch():
    """
    This is the main endpoint for the stitching command. Stitches the images using two different functions, in effect creating two stitches, just for redundancy purposes
    """
    img = stitch_image()
    img.show()
    img2 = stitch_image_own()
    img2.show()
    return jsonify({"result": "ok"})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
