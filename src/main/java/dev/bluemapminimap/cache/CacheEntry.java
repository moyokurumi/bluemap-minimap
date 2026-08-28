package dev.bluemapminimap.cache;

import dev.bluemapminimap.model.ResourceValidators;

public record CacheEntry(byte[] bytes, ResourceValidators validators) {
}
