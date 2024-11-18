package example;

import example.domain.Response;
import example.domain.game.Cave;
import example.domain.game.Direction;
import example.domain.game.Item;
import example.domain.game.Location;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Dijkstra {
    private final Cave cave;
    private final int[] cache;
    private final Map<Location, List<Response.StateLocations.ItemLocation>> locationItems;
    private final Map<Location, List<Response.StateLocations.PlayerLocation>> locationPLayers;

    Dijkstra(Cave cave, Map<Location, List<Response.StateLocations.ItemLocation>> locationItems, Map<Location, List<Response.StateLocations.PlayerLocation>> locationPLayers) {
        this.cave = cave;
        this.cache = new int[cave.rows() * cave.columns()];
        Arrays.fill(cache, Integer.MAX_VALUE);
        this.locationItems = locationItems;
        this.locationPLayers = locationPLayers;
    }

    private static Location next(Location location, Direction direction) {
        return switch (direction) {
            case Up -> new Location(location.row() - 1, location.column());
            case Down -> new Location(location.row() + 1, location.column());
            case Left -> new Location(location.row(), location.column() - 1);
            case Right -> new Location(location.row(), location.column() + 1);
        };
    }

    private int go(Location location, Direction direction, int cost) {
        final var next = next(location, direction);
        final var index = index(next);
        if (locationPLayers.get(location) != null) {
            return Integer.MAX_VALUE; // omit other players
        }

        if (cave.rock(next.row(), next.column())) {
            return Integer.MAX_VALUE; // omit rock
        }

        if (cost > 300) {
            return cost; // not to deep
        }

        final var current = cache[index];
        if (cost >= current) {
            return Integer.MAX_VALUE;
        }

        cache[index] = cost;

        final var items = locationItems.get(next);
        if (items != null) {
            final var optionalGold = items.stream()
                    .filter(item -> item.entity() instanceof Item.Gold)
                    .findAny();
            if (optionalGold.isPresent()) {
                return cost;
            }
        }

        final var costUp = go(next, Direction.Up, cost + 1);
        final var costDown = go(next, Direction.Down, cost + 1);
        final var costLeft = go(next, Direction.Left, cost + 1);
        final var costRight = go(next, Direction.Right, cost + 1);

        return Stream.of(costUp, costDown, costLeft, costRight).min(Integer::compareTo).get();
    }

    private int index(Location location) {
        return location.row() * cave.columns() + location.column();
    }

    public Direction find(Location start) {

        final var costUp = go(start, Direction.Up, 1);
        final var costDown = go(start, Direction.Down, 1);
        final var costLeft = go(start, Direction.Left, 1);
        final var costRight = go(start, Direction.Right, 1);

        Direction direction = Direction.Up;
        int cost = Integer.MAX_VALUE;
        if (costUp < cost) {
            cost = costUp;
            direction = Direction.Up;
        }
        if (costDown < cost) {
            cost = costDown;
            direction = Direction.Down;
        }
        if (costLeft < cost) {
            cost = costLeft;
            direction = Direction.Left;
        }
        if (costRight < cost) {
            cost = costRight;
            direction = Direction.Right;
        }

        return direction;
    }
}
