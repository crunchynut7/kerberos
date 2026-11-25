package com.bossarena.engine;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Breadth-first pathfinding on the arena grid. Diagonal steps are only allowed when both of the
 * tiles they cut past are open, so paths never clip the corner of a wall.
 */
final class Pathfinder
{
	/** West, east, south, north, then the diagonals. Earlier directions win ties. */
	private static final int[][] DIRECTIONS = {
		{-1, 0}, {1, 0}, {0, -1}, {0, 1},
		{-1, -1}, {1, -1}, {-1, 1}, {1, 1},
	};

	private Pathfinder()
	{
	}

	/**
	 * Finds the shortest path from {@code from} to the nearest tile matching {@code goal}.
	 * If no such tile can be reached, the path leads to the reachable tile closest to {@code aim}.
	 *
	 * @return the tiles to step through, excluding {@code from}; empty if already there
	 */
	static List<Tile> find(Tile from, Predicate<Tile> walkable, Predicate<Tile> goal, Tile aim)
	{
		Map<Tile, Tile> cameFrom = new HashMap<>();
		Deque<Tile> queue = new ArrayDeque<>();
		cameFrom.put(from, from);
		queue.add(from);

		Tile best = from;
		int bestDistance = from.distanceTo(aim);
		Tile found = null;

		while (!queue.isEmpty())
		{
			Tile current = queue.poll();
			if (goal.test(current))
			{
				found = current;
				break;
			}
			int distance = current.distanceTo(aim);
			if (distance < bestDistance)
			{
				best = current;
				bestDistance = distance;
			}
			for (int[] d : DIRECTIONS)
			{
				Tile next = current.plus(d[0], d[1]);
				if (cameFrom.containsKey(next) || !walkable.test(next))
				{
					continue;
				}
				boolean diagonal = d[0] != 0 && d[1] != 0;
				if (diagonal && (!walkable.test(current.plus(d[0], 0)) || !walkable.test(current.plus(0, d[1]))))
				{
					continue;
				}
				cameFrom.put(next, current);
				queue.add(next);
			}
		}

		Tile end = found != null ? found : best;
		LinkedList<Tile> path = new LinkedList<>();
		for (Tile t = end; !t.equals(from); t = cameFrom.get(t))
		{
			path.addFirst(t);
		}
		return Collections.unmodifiableList(path);
	}
}
