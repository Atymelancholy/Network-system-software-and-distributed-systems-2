package org.example.lab8;

import mpi.Group;
import mpi.Intracomm;
import mpi.MPI;
import mpi.MPIException;

final class GroupFactory {
    private GroupFactory() {
    }

    static Intracomm communicator(GroupPlan plan) throws MPIException {
        int rank = MPI.COMM_WORLD.Rank();
        return MPI.COMM_WORLD.Split(plan.colorOf(rank), rank);
    }

    static Group include(GroupPlan plan, int groupId) throws MPIException {
        return MPI.COMM_WORLD.Group().Incl(plan.membersOf(groupId));
    }

    static void printGroups(GroupPlan plan) throws MPIException {
        if (MPI.COMM_WORLD.Rank() != 0) {
            return;
        }
        System.out.println("MPI-группы (Group.Incl + Comm.Split):");
        for (int groupId = 0; groupId < plan.groupCount; groupId++) {
            Group group = include(plan, groupId);
            System.out.println("  " + plan.describe(groupId) + ", Group.Size=" + group.Size());
        }
    }
}
