CC=g++
texture: texture.cpp
	${CC} texture.cpp -o build/texture -fPIC -fstack-protector-strong -z relro -z now -flto -D_FORTIFY_SOURCE=2 -fPIE -Wall -Wextra -pedantic
	cppcheck texture.cpp
