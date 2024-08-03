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
