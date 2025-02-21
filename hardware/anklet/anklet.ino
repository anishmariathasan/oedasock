#include <Arduino.h>

// 引脚定义
#define STRETCH_SENSOR_PIN D9      // 拉伸传感器分压输入(ADC)
#define PUMP_IN_PIN D8             // 充气电机控制(PWM)
#define PUMP_OUT_PIN D7            // 放气电机控制(PWM)
#define HX710B_DATA D10            // 压力传感器数据线
#define HX710B_SCK D1              // 压力传感器时钟线

// HX710B相关参数
#define HX710B_GAIN 128            // 增益设置
unsigned long lastReadTime = 0;
const int readInterval = 100;       // 传感器读取间隔(ms)

// PID参数
float Kp = 1.0, Ki = 0.1, Kd = 0.05;
float setPoint = 100.0;            // 目标压力值(kPa)
float integral = 0, lastError = 0;

// 传感器校准
float baselineStretch = 0.0;
const int calibrationSamples = 50;

// 电机控制参数
const int pumpMaxSpeed = 255;      // PWM最大值
const int deadZone = 5;            // 压力控制死区(kPa)

void setup() {
  Serial.begin(115200);
  
  // 初始化引脚
  pinMode(STRETCH_SENSOR_PIN, INPUT);
  pinMode(PUMP_IN_PIN, OUTPUT);
  pinMode(PUMP_OUT_PIN, OUTPUT);
  pinMode(HX710B_DATA, INPUT);
  pinMode(HX710B_SCK, OUTPUT);

  // 校准拉伸传感器基线
  calibrateStretchSensor();
  
  // 初始化HX710B
  digitalWrite(HX710B_SCK, LOW);
  delay(10);
}

void loop() {
  // 读取传感器数据
  float currentPressure = readPressure();
  float stretchValue = readStretch();
  
  // PID计算
  float error = setPoint - currentPressure;
  integral += error * 0.1;         // 0.1为时间因子
  float derivative = (error - lastError) / 0.1;
  float control = Kp*error + Ki*integral + Kd*derivative;

  // 电机控制逻辑
  controlPump(control, currentPressure);

  // 更新状态
  lastError = error;
  
  // 调试输出
  Serial.print("Pressure: ");
  Serial.print(currentPressure);
  Serial.print(" kPa | Stretch: ");
  Serial.print(stretchValue);
  Serial.print(" | Control: ");
  Serial.println(control);
  
  delay(50);
}

// 校准拉伸传感器基线
void calibrateStretchSensor() {
  float sum = 0;
  for(int i=0; i<calibrationSamples; i++){
    sum += analogRead(STRETCH_SENSOR_PIN);
    delay(10);
  }
  baselineStretch = sum / calibrationSamples;
}

// 读取拉伸值（处理后）
float readStretch() {
  int raw = analogRead(STRETCH_SENSOR_PIN);
  return (raw - baselineStretch) / baselineStretch * 100; // 返回百分比变化
}

// 读取HX710B压力值
long readHX710B() {
  while(digitalRead(HX710B_DATA));
  
  long value = 0;
  for(int i=0; i<24; i++){
    digitalWrite(HX710B_SCK, HIGH);
    delayMicroseconds(1);
    value = (value << 1) | digitalRead(HX710B_DATA);
    digitalWrite(HX710B_SCK, LOW);
    delayMicroseconds(1);
  }
  
  // 设置增益
  for(int i=0; i<HX710B_GAIN; i++){
    digitalWrite(HX710B_SCK, HIGH);
    delayMicroseconds(1);
    digitalWrite(HX710B_SCK, LOW);
    delayMicroseconds(1);
  }
  return value;
}

// 转换为压力值(kPa)
float readPressure() {
  static float pressure = 0;
  if(millis() - lastReadTime > readInterval){
    long raw = readHX710B();
    pressure = raw * 0.001; // 根据传感器特性调整转换系数
    lastReadTime = millis();
  }
  return pressure;
}

// 电机控制
void controlPump(float control, float currentPressure) {
  // 死区处理
  if(abs(currentPressure - setPoint) < deadZone) {
    analogWrite(PUMP_IN_PIN, 0);
    analogWrite(PUMP_OUT_PIN, 0);
    return;
  }

  // 方向控制
  if(control > 0){
    int speed = constrain(map(abs(control), 0, 100, 0, pumpMaxSpeed), 0, 255);
    analogWrite(PUMP_IN_PIN, speed);
    analogWrite(PUMP_OUT_PIN, 0);
  } else {
    int speed = constrain(map(abs(control), 0, 100, 0, pumpMaxSpeed), 0, 255);
    analogWrite(PUMP_IN_PIN, 0);
    analogWrite(PUMP_OUT_PIN, speed);
  }
}