#
# @(#)GNUmakefile	1.21 03/09/11
#
# J2RE Installers for Solaris/SPARC
#

# Include platform-specific definitions
include ../../share/Platform.gmk
include ../minclude/solaris.defs

#FILES = j2re1_3_0/GNUmakefile_1.3.0_02 j2re1_4_0/GNUmakefile j2re1_4_1/GNUmakefile j2re1_4_1/GNUmakefile_1.4.1 j2re1_3_1/GNUmakefile

FILES = j2re1_4_2/GNUmakefile

all:: 
	for i in $(FILES) ; do \
	    echo ">>>Recursively making "$$i" "$@"..."; \
	    $(MAKE) -f $$i bundle || exit 1; \
	    echo "<<<Finished Recursively making "$$i" "$@"." ; \
	done

clean:: 
	for i in $(FILES) ; do \
	    echo ">>>Recursively making "$$i" "$@"..."; \
	    $(MAKE) -f $$i clean || exit 1; \
	    echo "<<<Finished Recursively making "$$i" "$@"." ; \
	done




