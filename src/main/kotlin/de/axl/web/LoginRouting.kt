package de.axl.web

import de.axl.db.UserService
import de.axl.respondCss
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.css.*
import kotlinx.html.*
import org.slf4j.Logger

fun Route.loginRoute(userService: UserService, logger: Logger) {
    post("/login") {
        val params = call.receiveParameters()
        val username = params["username"]
        val password = params["password"]
        val fromUI = params["fromui"] == "1"
        logger.info("Login request from ${if (fromUI) "UI" else "API"} for $username")
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            if (fromUI) {
                call.respondRedirect("/app/login?error=Missing username or password")
            } else {
                call.respond(HttpStatusCode.BadRequest, "Missing username or password")
            }
            return@post
        }
        val dbUser = userService.findByUsername(username)

        if (dbUser == null || !userService.testPassword(dbUser, password)) {
            logger.warn("Wrong credentials used! username: $username | password: $password")
            if (fromUI) {
                call.respondRedirect("/app/login?error=Wrong credentials")
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Wrong credentials")
            }
        } else {
            call.sessions.set(UserSession(username))
            if (fromUI) {
                call.respondRedirect("/app")
            } else {
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    get("/app/login") {
        val error = call.request.queryParameters["error"]
        call.respondHtml {
            head {
                link(rel = "stylesheet", href = "/app/styles/login.css", type = "text/css")
            }
            body {
                div("main") {
                    h1 {
                        +"Space"
                    }
                    h3 {
                        +"Login"
                    }
                    form(action = "/login", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                        p {
                            +"Username:"
                            textInput(name = "username")
                        }
                        p {
                            +"Password:"
                            passwordInput(name = "password")
                        }
                        textInput(name = "fromui", classes = "fromui") {
                            value = "1"
                        }
                        if (!error.isNullOrBlank()) {
                            div("error") {
                                p {
                                    +"Error: $error"
                                }
                            }
                        }
                        p {
                            submitInput { value = "Login" }
                        }
                    }
                }
            }
        }
    }

    get("/app/styles/login.css") {
        call.respondCss {
            body {
                backgroundColor = Color.darkGray
            }
            rule(".fromui") {
                display = Display.none
            }
            rule("div.main") {
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = Align.center
                fontSize = 12.pt
            }
            rule("div.error") {
                border = Border(2.px, BorderStyle.solid, Color.red)
                backgroundColor = Color.lightPink
                textAlign = TextAlign.center
            }
            rule("div.error > p") {
                color = Color.darkRed
                fontSize = 11.pt
            }
        }
    }
}