#ifndef WHILEY_H
#define WHILEY_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

// ============================================================
// Array Operations
// ============================================================
#define arr_t(T) struct {size_t len; T data[];}

#endif
