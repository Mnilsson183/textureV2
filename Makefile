CC=g++
texture: texture.c
	${CC} texture.cpp -o build/texture -fPIC -fstack-protector-strong -z relro -z now -flto -D_FORTIFY_SOURCE=2 -fPIE -Wall -Wextra -pedantic

run:
	./build/texture build/test.c

valgrind:
	valgrind -s --leak-check=full --show-leak-kinds=all build/texture build/test.c
