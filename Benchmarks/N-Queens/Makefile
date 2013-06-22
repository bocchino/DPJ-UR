ALL = sequential/classes parallel/classes instrument/classes

include ../Makefile.common

QUEENS=Queens
SIZE ?=14
TN ?= 2
FOREACH_CUTOFF ?= 7
SEQ_CUTOFF ?= 1

run-seq :
	$(DPJ-SEQ) $(QUEENS) TIME $(SIZE) $(SEQ_CUTOFF)

run-par:
	$(DPJ-PAR) $(QUEENS) --dpj-num-threads $(TN) --dpj-foreach-cutoff $(FOREACH_CUTOFF) TIME $(SIZE) $(SEQ_CUTOFF)

run-ideal :
	${DPJ-INST} $(QUEENS) IDEAL $(SIZE) $(SEQ_CUTOFF)
