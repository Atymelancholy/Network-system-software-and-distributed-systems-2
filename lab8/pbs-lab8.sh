#!/bin/bash
#PBS -N lab8-mpi
#PBS -l nodes=3:ppn=2
#PBS -l walltime=00:20:00
#PBS -j oe

cd "$PBS_O_WORKDIR"
export MPJ_HOME="${MPJ_HOME:-$PBS_O_WORKDIR/lib/mpj-v0_44}"
export PATH="$MPJ_HOME/bin:$PATH"

mvn -q compile
mpjrun.sh -np 6 -dev niodev -machinesfile "$PBS_NODEFILE" \
  -cp target/classes org.example.lab8.Lab8App 2 2048 nonblocking 8 "$PBS_O_WORKDIR/data"
