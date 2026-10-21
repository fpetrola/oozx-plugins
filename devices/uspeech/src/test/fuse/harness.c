/* Fuse's own SP0256, fed a ROM and a script, printing every sample with its T-state and every
   answer to "busy?". The Java port is run on the same script and must print the same. */
#include "sp0256.c"
#include <stdio.h>

libspectrum_dword tstates;
static fuse_machine_info info = { { 69888, 3500000 } };
fuse_machine_info *machine_current = &info;
static unsigned long frame_base;

void sound_sp0256_write( libspectrum_dword at, libspectrum_signed_word val )
{
  printf( "S %lu %d\n", frame_base + at, val );
}

int main( int argc, char **argv )
{
  static libspectrum_byte rom[ 4096 ];
  FILE *f = fopen( argv[1], "rb" );
  if( !f || fread( rom, 1, 2048, f ) != 2048 ) return 2;
  fclose( f );
  memcpy( rom + 2048, rom, 2048 );
  if( sp0256_reset( rom ) ) return 3;
  sp0256_set_clock( 3050000 );
  FILE *s = fopen( argv[2], "r" );
  int frame = 0, at_frame;
  long t, arg;
  char op;
  while( fscanf( s, "%d %ld %c %ld", &at_frame, &t, &op, &arg ) == 4 ) {
    while( frame < at_frame ) { sp0256_do_frame(); frame++; frame_base += 69888; }
    tstates = t;
    if( op == 'P' ) sp0256_play( arg );
    else if( op == 'B' ) printf( "B %lu %d\n", frame_base + t, sp0256_busy() );
    else if( op == 'C' ) sp0256_change_clock( arg );
  }
  return 0;
}
