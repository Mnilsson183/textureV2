CC=g++
texture: texture.c
	${CC} texture.c -o build/texture -Wall -Wextra -pedantic

memcheck:
	valgrind --leak-check=full ./build/texture