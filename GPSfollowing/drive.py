import threading
import time
import serial
from GpsAngle import calculateBearing, distance

"""
* Script that handles the logic behind the SmartCar autonomously following the traveler (phone application)
* @author Erik Laurin
* @version 1.0.2
"""

# serial for SmartCar
try:
    serial_arduino = serial.Serial('/dev/ttyACM0', 9600)
except Exception:
    serial_arduino = serial.Serial('/dev/ttyACM1', 9600)

# Phone GPS coordinates
phone_longitude, phone_latitude = 0, 0

# SmartCar GPS variables
smartcar_longitude, smartcar_latitude, smartcar_pdop, smartcar_fix = 0, 0, 0, 0

# helper variables
old_angle, exitFlag = 0, 0
no_fix_bol_phone, no_fix_bol_SmartCar = True, True
update_frequency = 0.2


def isfloat(value):
    """
    Checks if the value in the brackets qualifies as a float
    :param value: string that is going be checked
    :return: True if float
    """
    try:
        float(value)
        return True
    except:
        return False


def exit(device):
    """
    Terminates the script by raising the exit flag so the threads terminate
    :param device:  the device that intitiates the termination (phone or smartcar)
    """
    print(device + '. Exiting threads..')
    global exitFlag
    exitFlag = 1


def no_fix(device):
    """
    When GPS accuracy is too poor, this function is called and the SmartCar stops until the accuracy becomes suffice again
    :param device: the unit that has too poor GPS accuracy
    """
    global no_fix_bol_phone
    global no_fix_bol_SmartCar

    if device == 'phone':
        if not (no_fix_bol_phone):
            send_speed(0)
            no_fix_bol_phone = True
            print(device + ' - no fix (1). Setting speed to 0 and waiting for fix..')
        else:
            print('waiting for ' + device + ' fix..')
            time.sleep(1)
    elif device == 'SmartCar':
        if not (no_fix_bol_SmartCar):
            send_speed(0)
            no_fix_bol_SmartCar = True
            print(device + ' - no fix (1). Setting speed to 0 and waiting for fix..')
        else:
            print('waiting for ' + device + ' fix..')
            time.sleep(1)


class phone_coordinates(threading.Thread):
    def __init__(self, threadID, name):
        threading.Thread.__init__(self)
        self.threadID = threadID
        self.name = name

    def run(self):
        print("Starting " + self.name)
        get_phone_coordinates(self.name)
        print("Exiting " + self.name)


def get_phone_coordinates(threadName):
    """
    Receives and process the phone coordinates (also changes SmartCar mode and cancel the script)
    :param threadName: the name of the thread
    """
    while not exitFlag:
        # print ("%s: %s" % (threadName, time.ctime(time.time())))
        try:
            coords = open('/home/pi/repo/group-2/GPSfollowing/coords_phone.txt', "r", -1)
            phone_latitudes, phone_longitudes = coords.read().split()

            if (phone_latitudes == '0'):  # '0' codes for termination command from phone application
                exit('phone - exit command (0)')
            elif (phone_latitudes == '1'):  # '1' codes for that the phone has insufficient GPS signal
                no_fix('phone')
            elif (phone_latitudes == '2'):  # '2' codes for manual control and will be sent to SmartCar to change mode
                send_mode("M")
            elif (phone_latitudes == '3'):  # '3' codes for GPSfollowing and will be sent to SmartCar to change mode
                send_mode("G")
            elif isfloat(phone_latitudes):  # if float, the value is regarded as a valid coordinate
                global no_fix_bol_phone
                no_fix_bol_phone = False

            global phone_latitude
            global phone_longitude

            phone_latitude = float(phone_latitudes)
            phone_longitude = float(phone_longitudes)
            time.sleep(0.1)

        except ValueError:# happens when the file is modified while reading
            pass
        except Exception as e:
            print("", e)


class smartcar_coordinates(threading.Thread):
    def __init__(self, threadID, name):
        threading.Thread.__init__(self)
        self.threadID = threadID
        self.name = name

    def run(self):
        print("Starting " + self.name)
        get_smartcar_coordinates(self.name)
        print("Exiting " + self.name)


def get_smartcar_coordinates(threadName):
    """
    Receives and process the SmartCar coordinates
    :param threadName: the name of the thread
    """
    while not exitFlag:
        # print ("%s: %s" % (threadName, time.ctime(time.time())))
        try:
            coords = open('/home/pi/repo/group-2/GPSfollowing/coords_smartcar.txt', "r", -1)
            smartcar_latitudes, smartcar_longitudes, smartcar_pdops, smartcar_fixs = coords.read().split()

            if (smartcar_latitudes == '1'): # '1' codes for that the SmartCar has insufficient GPS signal
                no_fix('SmartCar')
                continue
            elif isfloat(smartcar_latitudes): # if float, the value is regarded as a valid coordinate
                global no_fix_bol_SmartCar
                no_fix_bol_SmartCar = False

            global smartcar_latitude
            global smartcar_longitude
            global smartcar_pdop
            global smartcar_fix

            smartcar_latitude = float(smartcar_latitudes)
            smartcar_longitude = float(smartcar_longitudes)
            smartcar_pdop = float(smartcar_pdops)
            smartcar_fix = float(smartcar_fixs)

            if smartcar_pdop > 6 or smartcar_fix not in [1, 2, 3]:  # if PDOP, position dilution of precision, is > 6
                no_fix('SmartCar')                                  # or there is no fix, the GPS signal is regarded insufficient

            time.sleep(0.1)
        except ValueError: # happens when the file is modified while reading
            pass
        except Exception as e:
            print("", e)
        """
        print('smartcar_lat: ' + str(smartcar_latitude) + '\n')
        print('smartcar_long: '+ str(smartcar_longitude) + '\n')
        print('smartcar_pdop: ' + str(smartcar_pdop) + '\n')
        print('smartcar_fix: ' + str(smartcar_fix) + '\n')
        """


class drive(threading.Thread):
    def __init__(self, threadID, name):
        threading.Thread.__init__(self)
        self.threadID = threadID
        self.name = name

    def run(self):
        print("Starting " + self.name)
        drive_smartcar(self.name)
        print("Exiting " + self.name)


def scale_speed(distance, min_speed, max_speed, min_distance, max_distance):
    """
    The speed of the SmartCar is scaled based on distance between the SmartCar and the traveler as well as the range for
    the speed and distance
    
    :param distance: distance from the traveler, phone application, to the SmartCar
    :param min_speed: the minimum speed to scale to
    :param max_speed: the maximum speed to scale to
    :param min_distance: the maximum distance to scale to
    :param max_distance: the maximum distance to scale to
    :return: a scaled value that corresponds to the two provided ranges 
    """
    return (min_speed - max_speed) * (distance - min_distance) / (max_distance - min_distance) + max_speed

def drive_smartcar(threadName):
    """
    The logic behind the autonomous following.
    Evaluates if the SmartCar should move, based on angle and distance to the phone,
    and if so, what speed it should have
    :param threadName: name of the thread
    """
    while not exitFlag:
        # print ("%s: %s" % (threadName, time.ctime(time.time())))

        global no_fix_bol_phone
        global no_fix_bol_SmartCar

        if no_fix_bol_phone or no_fix_bol_SmartCar: # if insufficient GPS signal, returns to top of function
            time.sleep(0.5)
            print('no_fix_bol')
            continue

        global old_angle
        global smartcar_latitude
        global smartcar_longitude
        global phone_latitude
        global phone_longitude

        #The distance, in meters, between the positions
        distance_between = distance(smartcar_latitude, phone_latitude, smartcar_longitude,
                                     phone_longitude) * 1000  # Multiplied with 1000 to get m from km
        #The angle, in degrees, between the two different positions
        angle = calculateBearing(smartcar_latitude, smartcar_longitude, phone_latitude, phone_latitude)
        if angle > 180:
            angle -= 360

        print('distance in m: ' + str(distance_between))
        print('angle: ' + str(angle))

        # sets speed depending on distance to traveler
        if distance_between <= 5:
            speed = scale_speed(distance_between, 30, 100, 0, 5)
        else:
            speed = 0

        # disregard minor changes in angle (5 degrees) for better fluency
        if abs(angle - old_angle) < 5:
            print('disregard minor changes in angle')
            time.sleep(1)
            continue
        else:
            old_angle = angle

            # sends angle
            send_angle(angle)

            # waits until the SmartCar has had enough time to turn
            time.sleep(2)

        # sends speed
        send_speed(speed)

        # determines the update frequency
        time.sleep(update_frequency)

        """
        Approach with setAngle() instead of rotate()/rotateOnSpot()
         
        # sends angle
        send_angle(angle)

        # waits until the SmartCar has had enough time to turn
        time.sleep(0.01)

        # sends speed
        send_speed(speed)

        """


def send_angle(angle):
    """
    Sends the turn angle to the SmartCar
    :param angle: in degrees how much the SmartCar should turn
    """
    a = 'A'
    byte_angle = str.encode(a + str(angle) + '\n')
    serial_arduino.write(byte_angle)
    print("Sent to SmartCar: " + byte_angle)


def send_speed(speed):
    """
    Sends the speed to the SmartCar
    :param speed: the speed the SmartCar should be set to
    """
    s = 'S'
    byte_speed = str.encode(s + str(speed) + '\n')
    serial_arduino.write(byte_speed)
    print("Sent to SmartCar: " + s + str(speed))

def send_mode(mode):
    """
    Sends the mode to the SmartCar
    :param mode: the mode the SmartCar should be set to
    """
    m = mode
    byte_mode = str.encode(m + '\n')
    serial_arduino.write(byte_mode)
    print("Sent to SmartCar: " + str(m))

# Overwrite old GPS coordinates on start of script
try:
    coords_phone = open('/home/pi/repo/group-2/GPSfollowing/coords_phone.txt', "w", -1)
    coords_smartcar = open('/home/pi/repo/group-2/GPSfollowing/coords_smartcar.txt', "w", -1)
    coords_phone.write('1 0')
    coords_smartcar.write('1 0 0 0')
    coords_phone.close()
    coords_smartcar.close()
except Exception as e:
    print("", e)

# Create new threads
thread_phone_coordinates = phone_coordinates(1, "Thread-phone_coordinates")
thread_smartcar_coordinates = smartcar_coordinates(2, "Thread-smartcar_coordinates")
thread_drive = drive(3, "Thread-drive")

# Start new Threads
thread_phone_coordinates.start()
thread_smartcar_coordinates.start()
time.sleep(1)
thread_drive.start()

# Awaits all threads to terminate before execution continues
thread_phone_coordinates.join()
thread_smartcar_coordinates.join()
thread_drive.join()

# Makes sure that the car stops when the script terminates and changes to manual mode
print("Sending stop command to SmartCar")
send_speed(0)
time.sleep(2)
send_mode("M")
print("Bye")