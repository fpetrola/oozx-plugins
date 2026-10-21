#include "libspectrum.h"
typedef struct { struct { libspectrum_dword tstates_per_frame; libspectrum_dword processor_speed; } timings; } fuse_machine_info;
extern fuse_machine_info *machine_current;
