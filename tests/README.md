# LanBall tests and test tools

These programs check the game. They are **not** part of the game and are **not** counted in its 999 lines.
They may use Java that the notes do not teach (for example lambdas), because the notes rule applies only to `src/`.

| File | What it does |
|---|---|
| `V7Test.java` | 35 checks over real TCP: register, wrong PIN, PIN never sent, damaged files, logins during play, update rate, chat, ready reset, collision, goal, Elo, badges, XP, restart |
| `LinkTest.java` | 9 checks with two real (hidden) LanBall windows: HOST, JOIN, team, READY, START, chat, LEAVE |
| `CornerTrace.java` | Prints the ball tick by tick when two players squeeze it against a wall or into a corner (scenario 0, 1 or 2) |
| `Demo.java` | Bot players (`Demo.Bot`) and a hidden full 2v2 match that saves frames |
| `Play.java` | Opens the real game window and fills the other three places with bots, so one person can play |

## Run (from the repository folder)

Compile the game, then the tests (Windows uses `;` in the class path, Linux and macOS use `:`):

```bash
javac -d out src/lanball/*.java
javac -cp out -d tests/out tests/lanball/*.java
java -cp "out:tests/out" lanball.V7Test
java -cp "out:tests/out" lanball.LinkTest
java -cp "out:tests/out" lanball.CornerTrace 0
java -cp "out:tests/out" lanball.Play practice-data
```

`V7Test` and `CornerTrace` use free ports. `LinkTest` and `Play` host on port 45888, so close any running LanBall host first.
