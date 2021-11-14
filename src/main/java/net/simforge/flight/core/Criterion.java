package net.simforge.flight.core;

import net.simforge.networkview.core.Position;

public interface Criterion {
    boolean meets(Position position);
}
