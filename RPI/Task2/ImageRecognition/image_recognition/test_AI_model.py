from ultralytics import YOLO

id_to_class = {
    0: 11,
    1: 12,
    2: 13,
    3: 14,
    4: 15,
    5: 16,
    6: 17,
    7: 18,
    8: 19,
    9: 20,
    10: 21,
    11: 22,
    12: 23,
    13: 24,
    14: 25,
    15: 26,
    16: 27,
    17: 28,
    18: 29,
    19: 30,
    20: 31,
    21: 32,
    22: 33,
    23: 34,
    24: 35,
    25: 36,
    26: 37,
    27: 38,
    28: 39,
    29: 40,
}

model = YOLO('best_bull.pt')
results = model('test_images/bull_1.jpg')
cls = results[0].boxes.cls
print(results)
# mdp_id = id_to_class[int(cls)]
results[0].save(filename='found_1.jpg')  # save to dis


