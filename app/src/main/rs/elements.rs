#pragma version(1)
#pragma rs java_package_name(com.idkjava.thelements.rs)
#pragma rs_fp_relaxed

typedef struct Element {
    uchar4 color;
} Element_t;
/* Fixed size: 256 */
Element_t *elements;

typedef struct Particle {
    float2 pos;
    float2 newPos;
    float2 vel;
    uchar element;
} Particle_t;
/* Fixed size: MAX_PARTICLES */
Particle_t *particles;

uint *allCoords;

uint workWidth;
uint workHeight;

// Iterates over all coordinates
void __attribute__((kernel)) updatePos(Particle_t part) {
    float2 newPos = part.pos + part.vel;
    if (newPos.x >= 0.0 && newPos.x < workWidth &&
        newPos.y >= 0.0 && newPos.y < workHeight) {
        part.newPos = newPos;
    }
}

void __attribute__((kernel)) updateVel(Particle_t part) {
    part.vel.y += 0.1;
}
