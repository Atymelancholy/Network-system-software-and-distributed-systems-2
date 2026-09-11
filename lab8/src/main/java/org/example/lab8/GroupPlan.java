package org.example.lab8;

import mpi.MPI;
import mpi.MPIException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class GroupPlan {
    final int groupCount;
    final int[] colors;
    final int[][] members;

    GroupPlan(int groupCount, int[] colors, int[][] members) {
        this.groupCount = groupCount;
        this.colors = colors;
        this.members = members;
    }

    int colorOf(int worldRank) {
        return colors[worldRank];
    }

    int[] membersOf(int groupId) {
        return members[groupId];
    }

    static GroupPlan create(int groupCount, int worldSize, long seed) {
        int groups = Math.min(groupCount, worldSize);
        int[] sizes = randomSizes(groups, worldSize, new Random(seed));
        int[] shuffled = shuffledRanks(worldSize, new Random(seed + 1));
        int[] colors = new int[worldSize];
        int[][] members = new int[groups][];
        int offset = 0;
        for (int groupId = 0; groupId < groups; groupId++) {
            members[groupId] = slice(shuffled, offset, sizes[groupId]);
            paint(colors, members[groupId], groupId);
            offset += sizes[groupId];
        }
        return new GroupPlan(groups, colors, members);
    }

    static GroupPlan broadcast(GroupPlan localPlan) throws MPIException {
        int[] meta = new int[]{localPlan == null ? 0 : localPlan.groupCount};
        MPI.COMM_WORLD.Bcast(meta, 0, 1, MPI.INT, 0);
        int[] colors = localPlan != null ? localPlan.colors : new int[MPI.COMM_WORLD.Size()];
        MPI.COMM_WORLD.Bcast(colors, 0, colors.length, MPI.INT, 0);
        return fromColors(meta[0], colors);
    }

    static GroupPlan fromColors(int groupCount, int[] colors) {
        int[][] members = new int[groupCount][];
        for (int groupId = 0; groupId < groupCount; groupId++) {
            members[groupId] = ranksWithColor(colors, groupId);
        }
        return new GroupPlan(groupCount, colors, members);
    }

    static int[] randomSizes(int groups, int processes, Random random) {
        int[] sizes = new int[groups];
        java.util.Arrays.fill(sizes, 1);
        int leftover = processes - groups;
        for (int i = 0; i < leftover; i++) {
            sizes[random.nextInt(groups)]++;
        }
        return sizes;
    }

    static int[] shuffledRanks(int worldSize, Random random) {
        List<Integer> ranks = new ArrayList<>();
        for (int rank = 0; rank < worldSize; rank++) {
            ranks.add(rank);
        }
        Collections.shuffle(ranks, random);
        int[] result = new int[worldSize];
        for (int i = 0; i < worldSize; i++) {
            result[i] = ranks.get(i);
        }
        return result;
    }

    static int[] slice(int[] ranks, int offset, int count) {
        int[] part = new int[count];
        System.arraycopy(ranks, offset, part, 0, count);
        java.util.Arrays.sort(part);
        return part;
    }

    static void paint(int[] colors, int[] ranks, int groupId) {
        for (int rank : ranks) {
            colors[rank] = groupId;
        }
    }

    static int[] ranksWithColor(int[] colors, int groupId) {
        int count = 0;
        for (int color : colors) {
            if (color == groupId) {
                count++;
            }
        }
        int[] ranks = new int[count];
        int i = 0;
        for (int rank = 0; rank < colors.length; rank++) {
            if (colors[rank] == groupId) {
                ranks[i++] = rank;
            }
        }
        return ranks;
    }

    String describe(int groupId) {
        int[] ranks = members[groupId];
        StringBuilder text = new StringBuilder();
        text.append("группа ").append(groupId)
                .append(" (").append(ranks.length).append(" процессов, ranks ");
        for (int i = 0; i < ranks.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(ranks[i]);
        }
        return text.append(')').toString();
    }
}
