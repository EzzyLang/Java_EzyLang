# EzyLang

EzyLang은 코틀린, 파이썬, 타입스크립트 등을 참고하여 만든 프로그래밍 언어입니다.

```
memo func fib(n: number): number {
    if (n <= 1) return n
    return fib(n - 1) + fib(n - 2)
}

println("fib(50) = ${fib(50)}")
```

## 실행

```bash
java -jar ezylang-1.0.0.jar <파일명>.ezy

java -jar ezylang-1.0.0.jar test <파일명>.ezy
```

## 빌드

```bash
./gradlew jar
```

---

## 문법

### 변수와 상수

```
name: string = "EzyLang"
age: number = 25
pi: number = 3.14
isActive: boolean = true

age = 26

$MAX_SIZE: number = 100
```

| 타입 | 설명 | 예시 |
|------|------|------|
| `number` | 숫자 (정수, 실수) | `42`, `3.14` |
| `string` | 문자열 (2글자 이상) | `"hello"` |
| `char` | 문자 (1글자) | `"A"` |
| `boolean` | 참/거짓 | `true`, `false` |
| `void` | 반환값 없음 | 함수 반환 타입 |
| `null` | 널 | `null` |

### 범위 제한 타입

```
age: number(0..150) = 25
age = 200  // 에러: Value 200.0 is out of range 0.0..150.0

grade: char("A", "B", "C", "D", "F") = "A"
grade = "Z"  // 에러: Value 'Z' is not allowed
```

### 연산자

```
a + b      a - b      a * b      a / b      a % b

x += 5     x -= 3     x *= 2     x /= 4     x %= 3

x++        x--        ++x        --x

a == b     a != b     a < b      a > b      a <= b     a >= b

a && b     a || b     !a

-x         +x
```

#### 연쇄 비교

```
if (10 < age < 30) { ... }
if (0 <= score <= 100) { ... }
```

### 문자열

```
name: string = "World"
println("Hello, ${name}!")
println("1 + 2 = ${1 + 2}")
println("result = ${math.sqrt(144)}")

text: string = "Hello, World!"
text.length()
text.charAt(0)
text.repeat(2)
text.split(", ")
```

### 출력

```
print("출력")
println("출력")
```

### 조건문

```
if (score >= 90) {
    println("A")
} else if (score >= 80) {
    println("B")
} else {
    println("F")
}

if (x > 5) break
if (flag) println("true")
```

### 반복문

```
for (i: number in 1 .. 10) {
    println(i)
}

for (i: number in 0 .. 10 .. 2) {
    println(i)
}

names: string[] = ["Alice", "Bob", "Charlie"]
for (name: string in names) {
    println(name)
}

while (count > 0) {
    count -= 1
}

for (i: number in 1 .. 100) {
    if (i % 2 == 0) continue
    if (i > 10) break
    println(i)
}
```

### 배열

```
numbers: number[] = [1, 2, 3, 4, 5]
names: string[] = ["Alice", "Bob"]

println(numbers[0])
numbers[0] = 99
```

| 메서드 | 설명 |
|--------|------|
| `length()` | 배열 길이 |
| `contains(elem)` | 포함 여부 |
| `indexOf(elem)` | 요소 위치 |
| `lastIndexOf(elem)` | 마지막 요소 위치 |
| `isEmpty()` | 비어있는지 확인 |
| `isNotEmpty()` | 비어있지 않은지 확인 |
| `sort()` | 정렬 |
| `reverse()` | 역순 |
| `shuffle()` | 무작위 섞기 |
| `remove(elem)` | 요소 제거 |
| `removeAt(index)` | 인덱스로 제거 |
| `clear()` | 전체 제거 |
| `addAll(arr)` | 배열 합치기 |
| `join(separator)` | 문자열로 합치기 |

### 함수

```
func greet(name: string): void {
    println("안녕하세요, ${name}!")
}

func add(a: number, b: number): number {
    return a + b
}

func factorial(n: number): number {
    if (n <= 1) return 1
    return n * factorial(n - 1)
}
```

### 메모이제이션

```
memo func fib(n: number): number {
    if (n <= 1) return n
    return fib(n - 1) + fib(n - 2)
}

println(fib(50))
```

### switch

```
switch (day) {
    case 1: {
        println("월요일")
        break
    }
    case 2: {
        println("화요일")
        break
    }
    default:
        println("기타")
}

switch (grade) {
    case "A" -> println("우수")
    case "B" -> println("양호")
    default:
        println("기타")
}
```

### 타입 체크 / 캐스팅

```
x: number = 42
println(x is number)
println(x is string)

s: string = "123"
num: number = s as number
println(num + 7)
```

### 모듈

#### 네임스페이스 import

```
import math

println(math.sqrt(144))
println(math.PI)
```

#### 글로벌 import

```
from math import *
println(sqrt(144))

from math import sqrt, $PI
println(sqrt(144))
println(PI)
```

#### 사용자 모듈

```
// mylib.ezy
func add(a: number, b: number): number {
    return a + b
}
$PI: number = 3.14159
```

```
from mylib import add, $PI
println(add(3, 4))
println(PI)
```

### 주석

```
// 한 줄 주석

/*
여러 줄 주석
*/
```

### 블록 스코프

```
x: number = 10
{
    y: number = 20
    println(y)
    println(x)
}
```

### 내장 테스트

```
func add(a: number, b: number): number {
    return a + b
}

test "덧셈 테스트" {
    assert add(1, 2) == 3
    assert add(-1, 1) == 0
}
```

```bash
java -jar ezylang-1.0.0.jar test app.ezy
# [PASS] 덧셈 테스트
# === 1 passed, 0 failed ===
```

---

## 내장 모듈

### math

```
import math

math.sqrt(144)        math.abs(-42)         math.pow(2, 10)
math.min(3, 7)        math.max(3, 7)
math.floor(3.7)       math.ceil(3.2)        math.round(3.5)
math.sin(x)           math.cos(x)           math.tan(x)
math.asin(x)          math.acos(x)          math.atan(x)
math.log(x)           math.log10(x)
math.random()         math.toRadians(deg)   math.toDegrees(rad)
math.PI               math.E
```

### str

```
import str

str.toUpperCase(s)    str.toLowerCase(s)    str.trim(s)
str.startsWith(s, p)  str.endsWith(s, p)    str.strContains(s, sub)
str.strIndexOf(s, sub) str.strLength(s)
str.substring(s, start, end)                 str.replace(s, old, new)
str.reverse(s)        str.padLeft(s, len, ch) str.padRight(s, len, ch)
str.format(num, decimals)
```

### arr

```
import arr

arr.range(1, 10)      arr.range(0, 10, 2)   arr.fill(5, 0)
arr.sum(nums)         arr.avg(nums)
arr.arrMin(nums)      arr.arrMax(nums)
arr.slice(nums, 1, 3) arr.count(nums, 2)
```

---

## 예제

`src/main/resources/examples/` 디렉토리에 예제 파일이 있습니다:

| 파일 | 내용 |
|------|------|
| `01_hello_world.ezy` | 기본 출력 |
| `02_variables.ezy` | 변수, 상수, 타입 |
| `03_arithmetic.ezy` | 산술, 복합 대입 연산자 |
| `04_string_interpolation.ezy` | 문자열 보간, 이스케이프 |
| `05_if_else.ezy` | 조건문, 논리/비교 연산자 |
| `06_loops.ezy` | for, while, break, continue |
| `07_arrays.ezy` | 배열 선언, 순회, 메서드 |
| `08_functions.ezy` | 함수, 재귀 |
| `09_switch.ezy` | switch 콜론/화살표 스타일 |
| `10_increment_decrement.ezy` | 증감 연산자 |
| `11_type_check_cast.ezy` | is, as |
| `12_string_methods.ezy` | 문자열 메서드 |
| `13_array_methods.ezy` | 배열 메서드 |
| `14_comments.ezy` | 주석 |
| `15_import_module.ezy` | 사용자 모듈 import |
| `16_nested_loops.ezy` | 중첩 반복 |
| `17_bubble_sort.ezy` | 버블 정렬 |
| `18_calculator.ezy` | 계산기 |
| `19_scope.ezy` | 블록 스코프 |
| `20_unary_operators.ezy` | 단항 연산자 |
| `21_chained_comparison.ezy` | 연쇄 비교 |
| `22_constrained_types.ezy` | 범위 제한 타입 |
| `23_test_block.ezy` | 내장 테스트 |
| `24_memoization.ezy` | 메모이제이션 |
| `25_math_module.ezy` | math 모듈 |
| `26_str_module.ezy` | str 모듈 |
| `27_arr_module.ezy` | arr 모듈 |
| `28_namespace_import.ezy` | 네임스페이스 import |
