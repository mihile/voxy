float getFragDistance(int fogShape, vec3 position) {
    return fogShape == 0 ? length(position)
            : max(length(position.xz), abs(position.y));
}
