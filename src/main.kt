@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

import Direction.*
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.memScoped
import ncurses.*
import kotlin.math.max
import kotlin.random.Random

fun main(): Unit = memScoped {
    initscr()
    defer { endwin() }
    noecho()
    curs_set(0)
    halfdelay(2)

    var game = Game(
        width = 39,
        height = 39,
        pacman = Pacman(
            position = Cell(5, 0)
        ),
        innerWalls = listOf(
            Cell(1, 1) to Cell(9, 9),
            Cell(1, 11) to Cell(9, 18),
            Cell(11, 1) to Cell(18, 9),
            Cell(11, 11) to Cell(18, 18),

            Cell(20, 1) to Cell(28, 9),
            Cell(20, 11) to Cell(28, 18),
            Cell(30, 1) to Cell(37, 9),
            Cell(30, 11) to Cell(37, 18),

            Cell(1, 20) to Cell(9, 28),
            Cell(1, 30) to Cell(9, 37),
            Cell(11, 20) to Cell(18, 28),
            Cell(11, 30) to Cell(18, 37),

            Cell(20, 20) to Cell(28, 28),
            Cell(20, 30) to Cell(28, 37),
            Cell(30, 20) to Cell(37, 28),
            Cell(30, 30) to Cell(37, 37)
        )
    )

    val window = newwin(game.height + 2, game.width + 2, 0, 0)!!
    defer { delwin(window) }

    var input = 0
    while (input.toChar() != 'q') {
        window.draw(game)

        input = wgetch(window)
        val direction = when (input.toChar()) {
            'i' -> up
            'j' -> left
            'k' -> down
            'l' -> right
            else -> null
        }

        game = game.update(direction)
    }
}

private fun CPointer<WINDOW>.draw(game: Game) {
    wclear(this)
    box(this, 0u, 0u)

    game.apples.forEach { mvwprintw(this, it.y + 1, it.x + 1, ".") }
    game.powerUps.forEach { mvwprintw(this, it.y + 1, it.x + 1, "P") }
    game.pacman.let { mvwprintw(this, it.position.y + 1, it.position.x + 1, it.sign(game)) }

    game.innerWalls.forEach { (start, end) ->
        (start.x..end.x).forEach { x ->
            (start.y..end.y).forEach { y ->
                mvwprintw(this, y + 1, x + 1, "|")
            }
        }
    }
    game.ghosts.forEach {
        mvwprintw(this, it.position.y + 1, it.position.x + 1, it.sign(game))
    }
    if (game.isOver) {
        mvwprintw(this, 0, 6, "Game Over")
        mvwprintw(this, 1, 3, "Your score is ${game.score}")
    }

    wrefresh(this)
}

data class Game(
    val width: Int,
    val height: Int,
    val pacman: Pacman,
    val innerWalls: List<Pair<Cell, Cell>>,
    val apples: List<Cell> = (0 until width).flatMap { x ->
        (0 until height).map { Cell(x, it) }.filter { position ->
            pacman.position != position &&
                innerWalls.notInWall(position)
        }
    },
    val ghosts: List<Ghost> = apples.filter { Random.nextDouble() < 4.0 / apples.size }.map { Ghost(it) },
    val powerUps: List<Cell> = apples.filter { Random.nextDouble() < 4.0 / apples.size },
    val totalApples: Int = apples.count(),
    val powerSteps: Int = 0

) {
    val score = totalApples - apples.size
    val isOver = apples.isEmpty() || (powerSteps == 0 && ghosts.map { it.position }.contains(pacman.position))

    fun update(direction: Direction?): Game {
        if (direction != null && !isOver) {
            val nextPosition = pacman.position.move(direction)
            if (isOpen(nextPosition)) return copy(
                pacman = Pacman(nextPosition, sign = !pacman.sign),
                apples = this.apples - nextPosition,
                powerSteps = if (nextPosition in powerUps) 100 else max(0, powerSteps - 1),
                powerUps = powerUps - nextPosition,
                ghosts = ghosts.filter { powerSteps == 0 || it.position != nextPosition }.map { it.move(this) }
            )
        }
        return this
    }

    fun isOpen(cell: Cell) = cell.x >= 0 && cell.y >= 0 && cell.x < width && cell.y < height
        && innerWalls.notInWall(cell) && (powerSteps > 0 || ghosts.map { it.position }.none { it.x == cell.x && it.y == cell.y })
}

private fun List<Pair<Cell, Cell>>.notInWall(position: Cell): Boolean = none {
    it.first.x <= position.x &&
        it.first.y <= position.y &&
        it.second.x >= position.x &&
        it.second.y >= position.y
}

data class Ghost(val position: Cell, val speed: Double = 0.9) {
    fun move(game: Game): Ghost {
        if (Random.nextDouble() > speed) return this
        val directions = values().filter { game.isOpen(position.move(it)) }
        val bestMove = if (game.powerSteps == 0) directions.minBy {
            distance(game, it)
        } else directions.maxBy {
            distance(game, it)
        }
        return if (bestMove != null)
            copy(position = position.move(bestMove))
        else this
    }

    private fun distance(game: Game, it: Direction): Int {
        val pacmanx = game.pacman.position.x
        val pacmany = game.pacman.position.y
        val nextPos = position.move(it)
        return (nextPos.x - pacmanx) * (nextPos.x - pacmanx) + (nextPos.y - pacmany) * (nextPos.y - pacmany)
    }

    fun sign(game: Game) = if (game.powerSteps > 0) "g" else "G"

}

data class Pacman(val position: Cell, val sign: Boolean = true) {
    fun sign(game: Game) =
        when ((game.powerSteps > 0) to sign) {
            true to true -> "C"
            true to false -> "O"
            false to true -> "c"
            else -> "o"
        }
}

data class Cell(val x: Int, val y: Int) {
    fun move(direction: Direction) =
        Cell(x + direction.dx, y + direction.dy)
}

enum class Direction(val dx: Int, val dy: Int) {
    // -->
    // |
    // v
    up(0, -1),
    down(0, 1),
    left(-1, 0),
    right(1, 0);
}