import os
import shutil
import time
import glob
from ultralytics import YOLO
from PIL import Image
import cv2
import random
import string
import numpy as np

def load_model():
    """
    Load the model from the local directory
    """
    # Load YOLOv8 model (Ultralytics API)
    # Keep the existing weights path; adjust if needed for your setup.
    weights_path = 'task2best.pt'
    #weights_path = 'bestL160epoch.pt'
    model = YOLO(weights_path)
    return model

# Global mapping: ID (int) -> label (str)
name_to_id = {
    0: 'A', 1: 'B', 2: 'Bullseye', 3: 'C', 4: 'D', 5: 'E', 6: 'F', 7: 'G', 8: 'H',
    9: 'S', 10: 'T', 11: 'U', 12: 'V', 13: 'W', 14: 'X', 15: 'Y', 16: 'Z',
    17: 'circle', 18: 'down', 19: 'eight', 20: 'five', 21: 'four', 22: 'left',
    23: 'nine', 24: 'one', 25: 'right', 26: 'seven', 27: 'six', 28: 'three',
    29: 'two', 30: 'up'
}

# Reverse mapping: label (str) -> ID (int)
label_to_id = {v: k for k, v in name_to_id.items()}


def get_random_string(length):
    """
    Generate a random string of fixed length

    Inputs
    ------
    length: int - length of the string to be generated

    Returns
    -------
    str - random string
    """
    result_str = ''.join(random.choice(string.ascii_letters) for _ in range(length))
    return result_str



def draw_own_bbox(img, x1, y1, x2, y2, label, color=(36, 255, 12), text_color=(0, 0, 0)):
    """
    Draw bounding box on the image with text label and save both the raw and annotated image in the 'own_results' folder

    Inputs
    ------
    img: numpy.ndarray - image on which the bounding box is to be drawn
    x1: int - x coordinate of the top left corner of the bounding box
    y1: int - y coordinate of the top left corner of the bounding box
    x2: int - x coordinate of the bottom right corner of the bounding box
    y2: int - y coordinate of the bottom right corner of the bounding box
    label: str - label to be written on the bounding box
    color: tuple - color of the bounding box
    text_color: tuple - color of the text label

    Returns
    -------
    None
    """
    # Reformat the label to {label name}-{label id}
    suffix_id = label_to_id.get(label, 'NA')
    label_for_file = f"{label}-{suffix_id}"
    label_for_display = f"{label}, ImgID:{suffix_id}"

    # Convert the coordinates to int
    x1 = int(x1)
    x2 = int(x2)
    y1 = int(y1)
    y2 = int(y2)

    # Create a random string (timestamp) to avoid filename collisions
    rand = str(int(time.time()))

    # Ensure output directory exists
    os.makedirs('own_results', exist_ok=True)

    # Save the raw image
    img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    cv2.imwrite(f"own_results/raw_image_{label_for_file}_{rand}.jpg", img)

    # Border
    h_img, w_img = img.shape[:2]
    border_thickness = 10
    border_color = (255, 0, 0)  # white border
    img = cv2.rectangle(img, (0, 0), (w_img - 1, h_img - 1), border_color, border_thickness)

    # Increase font size (was 0.6, now 1.2 - adjust as needed)
    font_scale = 1.5
    font_thickness = 2

    # Draw the bounding box
    img = cv2.rectangle(img, (x1, y1), (x2, y2), color, 2)

    # Image id label at top
    (w, h), _ = cv2.getTextSize(label_for_display, cv2.FONT_HERSHEY_SIMPLEX, font_scale, font_thickness)
    bg_top = max(0, y1 - h - 10)
    img = cv2.rectangle(img, (x1, bg_top), (x1 + w + 10, bg_top + h + 10), color, -1)
    img = cv2.putText(img, label_for_display, (x1 + 5, bg_top + h + 5), cv2.FONT_HERSHEY_SIMPLEX, font_scale, text_color, font_thickness)

    # Save the annotated image
    cv2.imwrite(f"own_results/annotated_image_{label_for_file}_{rand}.jpg", img)


def predict_image(image, model, signal):
    """
    Predict the image using the model and save the results in the 'runs' folder

    Inputs
    ------
    image: str - name of the image file
    model: ultralytics.YOLO - model to be used for prediction
    signal: str - signal to be used for filtering the predictions

    Returns
    -------
    str - predicted label
    """
    try:
        # Load the image
        img = Image.open(os.path.join('uploads', image))

        # Run inference and save annotated images under runs/detect/exp*
        results = model.predict(img, save=True, project='runs/detect', name='exp')

        # Parse YOLOv8 results into a list of dicts
        r = results[0]
        pred_list = []
        if r.boxes is not None and len(r.boxes) > 0:
            xyxy = r.boxes.xyxy.cpu().numpy()
            conf = r.boxes.conf.cpu().numpy()
            cls = r.boxes.cls.cpu().numpy().astype(int)
            names = model.names
            for i in range(xyxy.shape[0]):
                xmin, ymin, xmax, ymax = xyxy[i]
                name = names[cls[i]] if int(cls[i]) in names else str(int(cls[i]))
                row = {
                    'xmin': float(xmin),
                    'ymin': float(ymin),
                    'xmax': float(xmax),
                    'ymax': float(ymax),
                    'confidence': float(conf[i]),
                    'name': name,
                }
                row['bboxHt'] = row['ymax'] - row['ymin']
                row['bboxWt'] = row['xmax'] - row['xmin']
                row['bboxArea'] = row['bboxHt'] * row['bboxWt']
                pred_list.append(row)

        # Sort by bbox area descending
        pred_list.sort(key=lambda x: x['bboxArea'] if isinstance(x, dict) else -1, reverse=True)

        # Filter out Bullseye
        pred_list = [p for p in pred_list if p['name'] != 'Bullseye']

        # Initialize prediction to NA
        pred = 'NA'

        # Single detection case
        if len(pred_list) == 1:
            if pred_list[0]['name'] != 'Bullseye':
                pred = pred_list[0]

        # Multiple detections
        elif len(pred_list) > 1:
            # Filter by confidence and area
            pred_shortlist = []
            current_area = pred_list[0]['bboxArea']
            for row in pred_list:
                if (
                    row['name'] != 'Bullseye'
                    and row['confidence'] > 0.5
                    and (
                        (current_area * 0.8 <= row['bboxArea'])
                        or (row['name'] == 'One' and current_area * 0.6 <= row['bboxArea'])
                    )
                ):
                    pred_shortlist.append(row)
                current_area = row['bboxArea']

            if len(pred_shortlist) == 1:
                pred = pred_shortlist[0]
            else:
                # Use signal to filter further
                pred_shortlist.sort(key=lambda x: x['xmin'])
                if signal == 'L':
                    pred = pred_shortlist[0]
                elif signal == 'R':
                    pred = pred_shortlist[-1]
                else:
                    # Choose central if possible
                    for i in range(len(pred_shortlist)):
                        if 250 < pred_shortlist[i]['xmin'] < 774:
                            pred = pred_shortlist[i]
                            break
                    # If none central, choose largest area
                    if isinstance(pred, str):
                        pred_shortlist.sort(key=lambda x: x['bboxArea'])
                        pred = pred_shortlist[-1]

        # Draw and map to ID
        if not isinstance(pred, str):
            draw_own_bbox(np.array(img), pred['xmin'], pred['ymin'], pred['xmax'], pred['ymax'], pred['name'])

            image_id = str(label_to_id.get(pred['name'], 'NA'))
        else:
            image_id = 'NA'

        print(f"Final result: {image_id}")
        return image_id

    # If some error happened, we just return 'NA' so that the inference loop is closed
    except Exception as e:
        print(f"Final result: NA")
        return 'NA'


def predict_image_week_9(image, model):
    # Load the image
    img = Image.open(os.path.join('uploads', image))

    # Run inference and save annotated images to runs/detect/exp*
    results = model.predict(img, save=True, project='runs/detect', name='exp')
    r = results[0]
    pred = 'NA'
    pred_list = []

    if r.boxes is not None and len(r.boxes) > 0:
        xyxy = r.boxes.xyxy.cpu().numpy()
        conf = r.boxes.conf.cpu().numpy()
        cls = r.boxes.cls.cpu().numpy().astype(int)
        names = model.names
        for i in range(xyxy.shape[0]):
            xmin, ymin, xmax, ymax = xyxy[i]
            name = names[cls[i]] if int(cls[i]) in names else str(int(cls[i]))
            row = {
                'xmin': float(xmin),
                'ymin': float(ymin),
                'xmax': float(xmax),
                'ymax': float(ymax),
                'confidence': float(conf[i]),
                'name': name,
            }
            row['bboxHt'] = row['ymax'] - row['ymin']
            row['bboxWt'] = row['xmax'] - row['xmin']
            row['bboxArea'] = row['bboxHt'] * row['bboxWt']
            pred_list.append(row)
            

    # Sort by bbox area descending
    pred_list.sort(key=lambda x: x['bboxArea'] if isinstance(x, dict) else -1, reverse=True)
    score = 0.001
    # Choose first with confidence > 0.5 and not Bullseye
    for row in pred_list:
        if row['name'] != 'Bullseye' and row['confidence'] > 0.5:
            pred = row
            score = pred['confidence']
            break
        
    # Draw the bounding box on the image
    if not isinstance(pred, str):
        draw_own_bbox(np.array(img), pred['xmin'], pred['ymin'], pred['xmax'], pred['ymax'], pred['name'])

    # Return the image id
    if not isinstance(pred, str):
        image_id = str(label_to_id.get(pred['name'], 'NA'))
    else:
        image_id = 'NA'
    return image_id, score


def stitch_image():
    """
    Stitches the images in the folder together and saves it into runs/stitched folder
    """
    # Initialize path to save stitched image
    imgFolder = 'runs'
    stitchedPath = os.path.join(imgFolder, f'stitched-{int(time.time())}.jpeg')

    # Find all files that ends with ".jpg" (won't match the stitched images saved as .jpeg)
    imgPaths = glob.glob(os.path.join(imgFolder + "/detect/*/", "*.jpg"))

    # Open all images
    images = [Image.open(x) for x in imgPaths]
    if not images:
        raise ValueError("No images found to stitch in runs/detect/*/")

    # Get the width and height of each image
    width, height = zip(*(i.size for i in images))

    # Calculate the total width and max height of the stitched image
    total_width = sum(width)
    max_height = max(height)
    stitchedImg = Image.new('RGB', (total_width, max_height))
    x_offset = 0

    # Stitch the images together
    for im in images:
        stitchedImg.paste(im, (x_offset, 0))
        x_offset += im.size[0]

    # Ensure target folder exists and save
    os.makedirs(os.path.join('runs', 'originals'), exist_ok=True)
    stitchedImg.save(stitchedPath)

    # Move original images to "originals" subdirectory
    for img in imgPaths:
        shutil.move(img, os.path.join("runs", "originals", os.path.basename(img)))

    return stitchedImg


def stitch_image_own():
    """
    Stitches the images in the folder together and saves it into own_results folder

    Basically similar to stitch_image() but with different folder names and slightly different drawing of bounding boxes and text
    """
    imgFolder = 'own_results'
    stitchedPath = os.path.join(imgFolder, f'stitched-{int(time.time())}.jpeg')

    imgPaths = glob.glob(os.path.join(imgFolder + "/annotated_image_*.jpg"))
    imgTimestamps = [imgPath.split("_")[-1][:-4] for imgPath in imgPaths]

    sortedByTimeStampImages = sorted(zip(imgPaths, imgTimestamps), key=lambda x: x[1])

    images = [Image.open(x[0]) for x in sortedByTimeStampImages]
    if not images:
        raise ValueError("No annotated images found to stitch in own_results/")

    width, height = zip(*(i.size for i in images))
    total_width = sum(width)
    max_height = max(height)
    stitchedImg = Image.new('RGB', (total_width, max_height))
    x_offset = 0

    for im in images:
        stitchedImg.paste(im, (x_offset, 0))
        x_offset += im.size[0]

    stitchedImg.save(stitchedPath)

    return stitchedImg
