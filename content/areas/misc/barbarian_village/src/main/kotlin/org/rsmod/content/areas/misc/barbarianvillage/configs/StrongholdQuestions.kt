package org.rsmod.content.areas.misc.barbarianvillage.configs

internal data class SecurityAnswer(
    val text: String,
    val correct: Boolean = false,
    val response: String? = null,
)

internal data class SecurityQuestion(val text: String, val answers: List<SecurityAnswer>) {
    init {
        require(answers.size in 2..3) { "Door questions have 2 or 3 options: $text" }
        require(answers.count(SecurityAnswer::correct) == 1) { "Exactly one correct answer: $text" }
        require(answers.first { it.correct }.response != null) {
            "The correct answer's reply must be capture-proven: $text"
        }
    }

    // A null reply is unknown; the caller must not invent dialogue for it.
    fun replyTo(index: Int): String? {
        val answer = answers.getOrNull(index) ?: return null
        return answer.response ?: if (answer.correct) null else wrongFallback
    }

    fun isCorrect(index: Int): Boolean = answers.getOrNull(index)?.correct == true

    private val wrongFallback: String? =
        answers.firstOrNull { !it.correct && it.response != null }?.response
}

internal object StrongholdQuestions {
    val Bank: List<SecurityQuestion> =
        listOf(
            SecurityQuestion(
                text =
                    "To pass you must answer me this: A player trades you some valuable items, " +
                        "provides you a bond, then asks if you want to share your account so he " +
                        "can help you make progress. How do you respond?",
                answers =
                    listOf(
                        SecurityAnswer(
                            "Decline the offer and report that player.",
                            correct = true,
                            response =
                                "Correct! Never share your login details with another player.",
                        ),
                        SecurityAnswer("Give the player access since they are a higher level."),
                        SecurityAnswer("Tell them you'll trade your account info for theirs."),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: What do you do if someone asks you for your " +
                        "password or bank PIN to make you a player moderator?",
                answers =
                    listOf(
                        SecurityAnswer(
                            "Don't give them the information and send an 'Abuse report'.",
                            correct = true,
                            response =
                                "Correct! Press the 'Report Abuse' button and fill in the " +
                                    "offending player's name and the correct category.",
                        ),
                        SecurityAnswer(
                            "Don't tell them anything and ignore them.",
                            response =
                                "Quite good. But we should try to stop scammers. So please report " +
                                    "them using the 'Report Abuse' button.",
                        ),
                        SecurityAnswer("Give them the information they asked for."),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: How do I remove a hijacker from my account?",
                answers =
                    listOf(
                        SecurityAnswer("Ask on social media."),
                        SecurityAnswer("Email Old School RuneScape."),
                        SecurityAnswer(
                            "Use the Account Recovery system.",
                            correct = true,
                            response =
                                "Correct! Visiting oldschool.runescape.com and using the account " +
                                    "recovery system will allow you to kick hijackers off of your " +
                                    "account by locking it as stolen.",
                        ),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: You have been offered a free giveaway or " +
                        "double XP invitation via in- game chat or email. What should you do?",
                answers =
                    listOf(
                        SecurityAnswer(
                            "Report the incident and do not click any links.",
                            correct = true,
                            response =
                                "Correct! This is an attempt to obtain your account details. If " +
                                    "it happened in the game, report it via the Report Abuse " +
                                    "option.",
                        ),
                        SecurityAnswer("Respond quickly so as not to miss the offer."),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: Which of these is an important " +
                        "characteristic of a secure password?",
                answers =
                    listOf(
                        SecurityAnswer("It incorporates your real name or birthday."),
                        SecurityAnswer(
                            "It's never reused on other websites or accounts.",
                            correct = true,
                            response =
                                "Correct! Make sure to use a unique password to keep your account " +
                                    "secure.",
                        ),
                        SecurityAnswer("It's never changed over many months or years."),
                    ),
            ),
            SecurityQuestion(
                text = "To pass you must answer me this: How do I set a bank PIN?",
                answers =
                    listOf(
                        SecurityAnswer(
                            "Talk to any banker.",
                            correct = true,
                            response =
                                "Correct! Simply talking to a banker will give you the option to " +
                                    "set a bank PIN. Never use personal details for passwords or " +
                                    "bank PINs!",
                        ),
                        SecurityAnswer("Use the account management section on the website."),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: What is the best way to secure your account?",
                answers =
                    listOf(
                        SecurityAnswer(
                            "A long and complicated password because you have a good memory."
                        ),
                        SecurityAnswer(
                            "Two-factor authentication on your account and your registered email.",
                            correct = true,
                            response =
                                "Correct! Using each of these features will maximise your account " +
                                    "security!",
                        ),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: What do you do if someone asks you for your " +
                        "password or bank PIN to make you a member for free?",
                answers =
                    listOf(
                        SecurityAnswer("Give them the information they asked for."),
                        SecurityAnswer("Don't tell them anything and ignore them."),
                        SecurityAnswer(
                            "Don't tell them anything and click the 'Report Abuse' button.",
                            correct = true,
                            response =
                                "Correct! Press the 'Report Abuse' button and fill in the " +
                                    "offending player's name and the correct category.",
                        ),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: Hey adventurer! You've been randomly " +
                        "selected for a prize of 1 year of free membership! I'm just going to " +
                        "need some of your account details so I can put it on your account!",
                answers =
                    listOf(
                        SecurityAnswer("Wowee! Let me just write those down for you."),
                        SecurityAnswer(
                            "No way! I'm reporting you to Jagex!",
                            correct = true,
                            response =
                                "Correct! Make sure to report anybody asking for your account " +
                                    "login details.",
                        ),
                        SecurityAnswer("I'm not sure about this... let me get back to you on it."),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: Can I leave my account logged in while I'm " +
                        "out of the room?",
                answers =
                    listOf(
                        SecurityAnswer("Yes, for up to an hour."),
                        SecurityAnswer(
                            "No.",
                            correct = true,
                            response =
                                "Correct! This is the safest, both in terms of security and " +
                                    "keeping your items! Leaving your character logged in can " +
                                    "also allow someone to steal your items or entire account!",
                        ),
                        SecurityAnswer("Yes, if I'll be only a minute or two."),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: Is it safe to get someone to level your " +
                        "account?",
                answers =
                    listOf(
                        SecurityAnswer(
                            "Yes, so long as you change your password when they have finished."
                        ),
                        SecurityAnswer(
                            "Yes, so long as they don't raise its levels by too much."
                        ),
                        SecurityAnswer(
                            "No, you should never allow anyone to use your account.",
                            correct = true,
                            response =
                                "Correct! Allowing someone else to use your account means they " +
                                    "may steal it, or may get it banned by cheating on it.",
                        ),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: What do I do if my account is compromised?",
                answers =
                    listOf(
                        SecurityAnswer("Post on social media about it."),
                        SecurityAnswer(
                            "Secure my device and reset my password.",
                            correct = true,
                            response =
                                "Correct! Securing your systems with virus and malware scans is " +
                                    "important before resetting your account password to ensure " +
                                    "your details are not compromised again. Don't forget to " +
                                    "secure your email account too!",
                        ),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: What should I do if I receive an email " +
                        "asking me to verify my identity or Account details due to suspicious " +
                        "activity?",
                answers =
                    listOf(
                        SecurityAnswer("Email them back with the information it asks for."),
                        SecurityAnswer("Click the links in the email to visit the website."),
                        SecurityAnswer(
                            "Delete it - it's a fake!",
                            correct = true,
                            response =
                                "Correct! Jagex will NEVER email you unless you've used the " +
                                    "website to change your account. Account details can only be " +
                                    "verified through using account recovery systems on the Old " +
                                    "School RuneScape website, NOT through responding to emails.",
                        ),
                    ),
            ),
            SecurityQuestion(
                text =
                    "To pass you must answer me this: What do I do if a moderator asks me for my " +
                        "account details?",
                answers =
                    listOf(
                        SecurityAnswer("Tell them whatever they want to know."),
                        SecurityAnswer("Politely tell them no, and ignore them."),
                        SecurityAnswer(
                            "Politely tell them no, then use the 'Report Abuse' button.",
                            correct = true,
                            response =
                                "Correct! Report any attempt to gain your account details as it " +
                                    "is a very serious breach of Old School RuneScape's rules. " +
                                    "Never use personal details for passwords or bank PINs!",
                        ),
                    ),
            ),
        )
}
