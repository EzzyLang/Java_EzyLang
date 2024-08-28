# 이지랭 (EzyLang)
이지랭은 코틀린 파이썬 타입스크립트 등을 참고하여 코드를 짜기 편하게 만들어진 프로그래밍 언어입니다.
<br/>
~~안 편하다구요? 제가 편합니다~~

```
arr: number[] = [5, 2, 3, 4, 1]

print("정렬 전: ")
for (i: number in arr) {
  print("${i}")
}

sort(arr)

print("\n정렬 후: ")
for (i: number in arr) {
  print("${i}")
}

func sort(arr: number[]): void {
    n: number = arr.length()
    for (i: number in 0 .. n - 2) {
        for (j: number in 0 .. n - i - 2) {
            if (arr[j] >= arr[j + 1]) {
                temp: number = arr[j]
                arr[j] = arr[j + 1]
                arr[j + 1] = temp
            }
        }
    }
}
```

~~이지랭이 안 이지 하네요~~

# 문법

## 1. 변수 선언 및 기본 데이터 타입

EZY 언어는 정적 타입 언어로, 변수를 선언할 때 타입을 명시해야 합니다.

- 숫자(number): `num: number = 10`
- 문자열(string): `str: string = "Hello, EZY!"`
- 불리언(boolean): `bool: boolean = true`
- 문자(char): `ch: char = 'A'`
- null: `n: null = null`

## 2. 상수 선언

상수는 `$` 기호를 사용하여 선언합니다:

```
$PI: number = 3.14159
```

## 3. 연산자

### 산술 연산자
- 덧셈: `+`
- 뺄셈: `-`
- 곱셈: `*`
- 나눗셈: `/`
- 나머지: `%`

### 비교 연산자
- 같음: `==`
- 다름: `!=`
- 크다: `>`
- 작다: `<`
- 크거나 같다: `>=`
- 작거나 같다: `<=`

### 논리 연산자
- AND: `&&`
- OR: `||`

## 4. 문자열 연산

문자열 연결은 `+` 연산자를 사용합니다:

```
str1 + ", " + str2
```

## 5. 타입 체크

`is` 키워드를 사용하여 타입을 확인할 수 있습니다:

```
num is number
```

## 6. 조건문

if-else 문을 사용합니다:

```
if (조건) {
    // 코드
} else if (조건) {
    // 코드
} else {
    // 코드
}
```

## 7. 반복문

for 루프를 사용하여 반복할 수 있습니다. 증가 단위를 지정할 수 있는 기능이 있습니다:

```
for (i: number in 시작..끝..증가단위) {
    // 코드
}
```

예시:
```
// 1부터 5까지 1씩 증가
for (i: number in 1..5) {
    // 코드
}

// 1부터 5까지 2씩 증가 (1, 3, 5)
for (i: number in 1..5..2) {
    // 코드
}
```

## 8. 배열

배열은 `타입[]`를 사용하여 선언하고 초기화합니다:

```
arr: number[] = [1, 2, 3, 4, 5]
```

배열 요소에 접근하고 수정할 수 있습니다:

```
arr[0] = 10
```

배열 메서드:
- `length()`: 배열의 길이를 반환
- `add(element)`: 배열에 요소 추가
- `remove(index)`: 지정된 인덱스의 요소 제거
- `get(index)`: 지정된 인덱스의 요소 반환
- `set(index, element)`: 지정된 인덱스에 요소 설정
- `containsAll(collection)`: 모든 요소 포함 여부 확인
- `clear()`: 모든 요소 제거
- `addAll(collection)`: 컬렉션의 모든 요소 추가
- `contains(element)`: 요소 포함 여부 확인
- `indexOf(element)`: 요소의 인덱스 반환
- `isEmpty()`: 배열이 비어있는지 확인
- `removeAll(collection)`: 컬렉션의 모든 요소 제거

## 9. 다차원 배열

다차원 배열도 지원합니다:

```
matrix: number[][] = [[1, 2], [3, 4], [5, 6]]
```

## 10. 함수

함수는 `func` 키워드를 사용하여 선언합니다:

```
func 함수이름(매개변수: 타입): 반환타입 {
    // 함수 본문
}
```

void 함수는 반환 타입을 `void`로 지정합니다.

## 12. Null 처리

null 값을 체크할 수 있습니다:

```
if (변수 is null) {
    // 코드
}
```

## 13. 문자열 템플릿

문자열 내에서 `${}` 구문을 사용하여 변수를 포함할 수 있습니다:

```
"${name} language is ${age} year old"
```

## 14. 기타 문자열 메서드

- `repeat(count)`: 문자열을 지정된 횟수만큼 반복