package org.rsmod.content.quest.area.portsarim.pandemonium

import dev.openrune.types.MesAnimType
import org.rsmod.api.player.dialogue.Dialogue

internal enum class PandemoniumScene {
    INTRODUCTION,
    INTERVIEW,
    BOARDING,
    ABOARD,
    WRECK,
    BETRAYAL,
    WAKE,
    STEVE,
    RIBS,
    STEVE_BOUNTY,
    JIM,
    JIM_SHIPYARD,
    BUILD_INSTRUCTIONS,
    BUILD_FINISHED,
    CARGO_LOG,
    CARGO_ASSIGNMENT,
    POST_DELIVERY,
    GROG,
    COMPLETION,
}

internal suspend fun Dialogue.playPandemoniumScene(scene: PandemoniumScene) {
    when (scene) {
        PandemoniumScene.INTRODUCTION -> {
            say(
                PandemoniumSpeaker.ANNE_SARIM,
                happy,
                "Ah, look what we have here, Will! This looks like someone<br>" +
                    "who needs a good job!",
            )
            chatPlayer(confused, "What?")
            say(
                PandemoniumSpeaker.WILL_SARIM,
                happy,
                "Goodness, Anne, I think you're right! This one's clearly<br>" +
                    "never worked an honest day in their life, and it's about<br>" +
                    "time someone changed that!",
            )
            chatPlayer(confused, "But I don't need a...")
            say(
                PandemoniumSpeaker.ANNE_SARIM,
                happy,
                "Well, let's not waste any more time! Stranger, are you<br>" +
                    "ready for your interview?",
            )
        }
        PandemoniumScene.INTERVIEW -> {
            chatPlayer(confused, "I guess so...?")
            say(
                PandemoniumSpeaker.WILL_SARIM,
                happy,
                "Let's get you started with a nice easy question: What is<br>" +
                    "your name?",
            )
            chatPlayer(neutral, "${player.displayName}.")
            say(
                PandemoniumSpeaker.ANNE_SARIM,
                happy,
                "Pleased to meet you, ${player.displayName}. I'm Anne, and this is<br>" +
                    "Will.",
            )
            say(
                PandemoniumSpeaker.WILL_SARIM,
                happy,
                "Next question: Do you have any experience captaining<br>" +
                    "a ship?",
            )
            chatPlayer(neutral, "Not really...")
            say(PandemoniumSpeaker.WILL_SARIM, sad, "Oh...")
            say(
                PandemoniumSpeaker.ANNE_SARIM,
                happy,
                "Will, it's important we not be too picky! It's a<br>" +
                    "competitive market, after all.",
            )
            say(
                PandemoniumSpeaker.WILL_SARIM,
                happy,
                "Okay, final question: Do you agree to waive all rights to<br>" +
                    "pursue legal action against your new employer in the<br>" +
                    "event of injury or horrific death?",
            )
            chatPlayer(confused, "Is that likely to happen?")
            say(PandemoniumSpeaker.ANNE_SARIM, happy, "Not at all!")
            chatPlayer(confused, "I'm not sure if I...")
            say(
                PandemoniumSpeaker.WILL_SARIM,
                happy,
                "Excellent! Well then, ${player.displayName}, I'm pleased to say that<br>" +
                    "we have the results of your interview. You bring some<br>" +
                    "impressive stuff to the table. We'd love to offer you the<br>" +
                    "role!",
            )
            say(PandemoniumSpeaker.ANNE_SARIM, happy, "Congratulations!")
            chatPlayer(
                confused,
                "Thanks... only, you didn't actually mention what the<br>" +
                    "role was...",
            )
            say(
                PandemoniumSpeaker.WILL_SARIM,
                happy,
                "Ah, how forgetful of us! Given that you've already<br>" +
                    "accepted, how about we discuss the details on our ship.",
            )
            chatPlayer(confused, "But I didn't accept anything...")
            say(
                PandemoniumSpeaker.ANNE_SARIM,
                happy,
                "Just let us know when you're ready, and we'll hop<br>" +
                    "aboard!",
            )
        }
        PandemoniumScene.BOARDING -> {
            chatPlayer(confused, "I guess I'm ready...")
            say(PandemoniumSpeaker.WILL_SARIM, happy, "Then let us away!")
        }
        PandemoniumScene.ABOARD -> {
            say(PandemoniumSpeaker.WILL_BOAT, happy, "Lovely! The open sea!")
            chatPlayer(
                confused,
                "It's not really that open... We haven't even left port<br>" +
                    "yet.",
            )
            say(
                PandemoniumSpeaker.ANNE_BOAT,
                happy,
                "Well, good news, ${player.displayName}, because that's where you<br>" +
                    "come in! You're our new helm!",
            )
            chatPlayer(confused, "Helm?")
            say(
                PandemoniumSpeaker.WILL_BOAT,
                happy,
                "It means you're in charge of the ship's helm! In other<br>" +
                    "words, you're responsible for steering the ship!",
            )
            chatPlayer(confused, "It's more of a boat really...")
            say(PandemoniumSpeaker.ANNE_BOAT, happy, "Well, we all have to start somewhere!")
            chatPlayer(
                confused,
                "Right... So if I'm manning the helm, what will you two<br>" +
                    "be doing?",
            )
            say(
                PandemoniumSpeaker.WILL_BOAT,
                happy,
                "Well, it's simple, isn't it? We're in charge!",
            )
            chatPlayer(confused, "Have you ever actually done this before?")
            say(PandemoniumSpeaker.ANNE_BOAT, happy, "Many times!")
            say(
                PandemoniumSpeaker.WILL_BOAT,
                happy,
                "Just never on our own ship before! This is an exciting<br>" +
                    "first day for... well, we haven't actually named our<br>" +
                    "business yet, but I'm sure we'll come up with a good<br>" +
                    "name soon.",
            )
            chatPlayer(confused, "And what does this business do?")
            say(PandemoniumSpeaker.ANNE_BOAT, happy, "We salvage shipwrecks of course!")
            chatPlayer(
                confused,
                "Of course. Alright then, I don't remember doing it, but<br>" +
                    "apparently I committed to this. Where is our first<br>" +
                    "wreck?",
            )
            say(
                PandemoniumSpeaker.WILL_BOAT,
                happy,
                "We've got a good one to get us started! Last time we<br>" +
                    "were on the Pandemonium, we purchased a map that<br>" +
                    "should lead us to an old pirate wreck.",
            )
            chatPlayer(confused, "The Pandemonium?")
            say(
                PandemoniumSpeaker.ANNE_BOAT,
                happy,
                "Have you never been? It's a great place for a drink!<br>" +
                    "We'll have to take you there!",
            )
            say(
                PandemoniumSpeaker.WILL_BOAT,
                happy,
                "But first, let's collect some salvage! According to the<br>" +
                    "map, the wreck is just east of the Karamja Shipyard, so<br>" +
                    "south of here.",
            )
            say(
                PandemoniumSpeaker.ANNE_BOAT,
                happy,
                "Over to you, ${player.displayName}! Take the helm when ready.",
            )
        }
        PandemoniumScene.WRECK -> {
            say(
                PandemoniumSpeaker.WILL_BOAT,
                happy,
                "Right, now that we're here, I think it's time to do some<br>" +
                    "salvaging!",
            )
            say(
                PandemoniumSpeaker.ANNE_BOAT,
                happy,
                "When you're ready, ${player.displayName}, just lower the salvaging<br>" +
                    "hook and let's see what we can find!",
            )
            chatPlayer(
                confused,
                "Hang on... why am I lowering the hook? I thought the<br>" +
                    "helm was my responsibility?",
            )
            say(PandemoniumSpeaker.WILL_BOAT, happy, "Yes! The helm... and the hook!")
            chatPlayer(
                confused,
                "Of course. I'll just do everything while you two reap<br>" +
                    "the rewards. Sounds about right.",
            )
            say(
                PandemoniumSpeaker.WILL_BOAT,
                happy,
                "Don't go getting grumpy on us now, ${player.displayName}. This<br>" +
                    "isn't about the rewards. It's all about the experience!",
            )
            say(
                PandemoniumSpeaker.ANNE_BOAT,
                happy,
                "That's right! Think of what you'll learn today!",
            )
            chatPlayer(sad, "*sigh*")
        }
        PandemoniumScene.BETRAYAL -> {
            chatPlayer(neutral, "Right then, let's give this a go.")
            say(PandemoniumSpeaker.WILL_CUTSCENE, quiz, "Well?")
            chatPlayer(quiz, "What do you make of these?")
            say(PandemoniumSpeaker.WILL_CUTSCENE, happy, "The medallion! Anne, we've found it!")
            chatPlayer(confused, "You were looking for this?")
            say(
                PandemoniumSpeaker.ANNE_CUTSCENE,
                happy,
                "That's right! And now that we've found it, I'm afraid we<br>" +
                    "don't really need you anymore.",
            )
            chatPlayer(confused, "What?")
            say(
                PandemoniumSpeaker.ANNE_CUTSCENE,
                happy,
                "Sorry, ${player.displayName}. If it's any consolation, you did a<br>" +
                    "great job! Unfortunately, we just have to let you go.",
            )
        }
        PandemoniumScene.WAKE -> {
            chatPlayer(drunk, "Eugh...")
            chatPlayer(confused, "Where am I?")
        }
        PandemoniumScene.STEVE -> {
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Yo ho ho and a Jolly Roger to you, me hearty! I can<br>" +
                    "see from the fact that you're totally drenched from<br>" +
                    "head to toe that you are a fellow scallywag sailor! The<br>" +
                    "name's Steve Beanie! Pleased to meet you.",
            )
            chatPlayer(
                confused,
                "I think I must be having some sort of fever dream... or<br>" +
                    "maybe I'm dead?",
            )
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Ha! Far from it, matey! Though the fever dream I<br>" +
                    "can't comment on. They're rather common around<br>" +
                    "here... but I assure you that it's well-nigh certainly like<br>" +
                    "enough most likely not the rum doing it!",
            )
            chatPlayer(
                confused,
                "Okay... You said words there, but I don't think they<br>" +
                    "actually formed a sentence.",
            )
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Ah, me wee hearty! You seem to be having quite the<br>" +
                    "crisis! Thankfully, you've rigged the starboard and<br>" +
                    "docked yourself at the right port! Welcome to the<br>" +
                    "Pandemonium!",
            )
            chatPlayer(confused, "Pandemonium?")
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Aye! To be sure! This here is the finest establishment in<br>" +
                    "all the seas. Sit back! Have a drink! Let the good times,<br>" +
                    "and the rum, flow!",
            )
            chatPlayer(
                confused,
                "If this is the Pandemonium, do you know of a pair of<br>" +
                    "salvagers called Will and Anne?",
            )
            say(
                PandemoniumSpeaker.STEVE,
                angry,
                "Avast ye! You name a pair of pinching looting<br>" +
                    "plundering seadogs!",
            )
            chatPlayer(
                neutral,
                "I see. So you do know them. They claimed to have<br>" +
                    "purchased a map here, but I'm guessing they weren't<br>" +
                    "being totally honest about that...",
            )
            say(
                PandemoniumSpeaker.STEVE,
                angry,
                "Purchased?! Purchased?! You're pulling my leg, you<br>" +
                    "scallywag! Those scurvy dogs came here and broke the<br>" +
                    "one rule of the Pandemonium! That map was pilfered!<br>" +
                    "Stolen!",
            )
            chatPlayer(
                confused,
                "Well, I had quite the fun run in with them myself.<br>" +
                    "They had me help them salvage an old shipwreck before<br>" +
                    "throwing me overboard.",
            )
            say(PandemoniumSpeaker.STEVE, sad, "Arrr, you poor scallywag.")
            chatPlayer(
                neutral,
                "Thank you. It was the map they stole that led them to<br>" +
                    "the wreck. What do you know about it?",
            )
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Not a lot, I'm afraid. You should hoist your sails and<br>" +
                    "go speak to Ribs instead. It was his map they looted.<br>" +
                    "He's the old dog over in the corner. Avast!",
            )
        }
        PandemoniumScene.RIBS -> {
            say(
                PandemoniumSpeaker.RIBS,
                neutral,
                "Ahoy, young whippersnapper. Have you come to hear a<br>" +
                    "tale from old Ribs?",
            )
            chatPlayer(
                confused,
                "Kind of, I guess. I was hoping you could tell me about<br>" +
                    "Will and Anne, along with the map they stole.",
            )
            say(
                PandemoniumSpeaker.RIBS,
                sad,
                "A dark day. A very dark day. Those young fools know<br>" +
                    "not what they have wrought upon themselves.",
            )
            chatPlayer(quiz, "That being?")
            say(
                PandemoniumSpeaker.RIBS,
                shocked,
                "A curse! A curse most foul! That map marked the<br>" +
                    "resting place of the Crimson Kraken! She was a deadly<br>" +
                    "vessel, captained by none other than Barnacle Barnaby<br>" +
                    "himself!",
            )
            chatPlayer(quiz, "Barnacle Barnaby? Who's that then?")
            say(
                PandemoniumSpeaker.RIBS,
                shocked,
                "You have not heard the tale of Barnacle Barnaby? You<br>" +
                    "have not heard of the scourge of the Unquiet Ocean?",
            )
            chatPlayer(neutral, "No, tell me about him.")
            say(
                PandemoniumSpeaker.RIBS,
                neutral,
                "He was a cruel and vicious pirate. Orchestrator of the<br>" +
                    "Driftwood Massacre and unleasher of the Plague of<br>" +
                    "Black Mists. He believed himself untouchable, but his<br>" +
                    "arrogance was his undoing.",
            )
            say(
                PandemoniumSpeaker.RIBS,
                neutral,
                "Despite the warnings of many, Barnacle Barnaby<br>" +
                    "attempted to plunder the Skullhorde, but his only<br>" +
                    "reward was a deadly curse. It destroyed the Crimson<br>" +
                    "Kraken, condemning all on board to a watery grave.",
            )
            chatPlayer(
                quiz,
                "And you had a map leading to the wreck of the<br>" +
                    "Kraken?",
            )
            say(
                PandemoniumSpeaker.RIBS,
                sad,
                "Aye! I purchased the map from Ulrich the Unfortunate,<br>" +
                    "the sole survivor of the Kraken, but I had no intention<br>" +
                    "of using it. I knew that no good would ever come from<br>" +
                    "seeking the Kraken.",
            )
            chatPlayer(quiz, "Then why not destroy the map?")
            say(
                PandemoniumSpeaker.RIBS,
                shocked,
                "And risk the wrath of Barnaby's ghost? No. Better the<br>" +
                    "map be locked away, so that neither it nor the curse<br>" +
                    "could harm anyone else.",
            )
            chatPlayer(
                confused,
                "I'm not sure I quite follow your reasoning for keeping<br>" +
                    "it, but I guess it doesn't matter now. I'm afraid Will<br>" +
                    "and Anne found and looted the Kraken.",
            )
            say(
                PandemoniumSpeaker.RIBS,
                sad,
                "I feared as much, though part of me still hoped it would<br>" +
                    "not come to be. Tell me, did they find Barnaby's<br>" +
                    "medallion?",
            )
            chatPlayer(
                neutral,
                "They did. It was actually the only thing they were<br>" +
                    "interested in.",
            )
            say(
                PandemoniumSpeaker.RIBS,
                shocked,
                "Then the foul curse is now free once more. They must<br>" +
                    "be stopped!",
            )
            chatPlayer(
                neutral,
                "Well, I do have my own personal reasons for wanting<br>" +
                    "Will and Anne dealt with. Maybe I could try and find<br>" +
                    "them.",
            )
            say(
                PandemoniumSpeaker.RIBS,
                happy,
                "You are a brave soul indeed, but that is just what is<br>" +
                    "needed at a time like this.",
            )
            say(
                PandemoniumSpeaker.RIBS,
                happy,
                "You should talk to Steve. He already put out a bounty<br>" +
                    "for Will and Anne after the theft. He might have heard<br>" +
                    "something about where they could have gone.",
            )
        }
        PandemoniumScene.STEVE_BOUNTY -> {
            say(PandemoniumSpeaker.STEVE, happy, "Yarr! What will it be?")
            chatPlayer(quiz, "About Will and Anne...")
            say(
                PandemoniumSpeaker.STEVE,
                angry,
                "Those plundering seadogs! Did you speak to Ribs about<br>" +
                    "them?",
            )
            chatPlayer(
                quiz,
                "I did. He seems to think they might have unleashed<br>" +
                    "some sort of curse.",
            )
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Ah, the curse of Barnacle Barnaby! It's one of Ribs'<br>" +
                    "favourite tales. Just a myth of course, but the old dog<br>" +
                    "does love his stories.",
            )
            chatPlayer(
                quiz,
                "Well, myths aside, Ribs mentioned you'd put a bounty<br>" +
                    "out for Will and Anne. I'd be quite keen to track them<br>" +
                    "down myself.",
            )
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Yo ho ho! A mighty good decision! It's a generous<br>" +
                    "bounty indeed. You don't go breaking the one rule of<br>" +
                    "the Pandemonium and expect to get away with it!",
            )
            chatPlayer(
                quiz,
                "Do you have any idea where Will and Anne might have<br>" +
                    "gone?",
            )
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Shiver me timbers! I'm afraid not. Old Grog said he<br>" +
                    "might try and track them down. Perhaps he has an idea<br>" +
                    "of where to find them.",
            )
            chatPlayer(quiz, "And where would I find Old Grog?")
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Ah, the old scallywag left not that long ago, but he'll be<br>" +
                    "back here before long. He enjoys a drink far too much!<br>" +
                    "In the meantime, you should hoist the sails and ready<br>" +
                    "your vessel!",
            )
            chatPlayer(confused, "But I don't have a vessel...")
            say(PandemoniumSpeaker.STEVE, confused, "What? But then how did you get here?")
            chatPlayer(
                confused,
                "Will and Anne threw me overboard and I washed up<br>" +
                    "here. I did mention this earlier...",
            )
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Blistering barnacles! Well, you won't be getting far<br>" +
                    "without a boat of your own. You should head to the<br>" +
                    "docks and speak with Junior Jim, our local shipwright.<br>" +
                    "He'll get you sorted out.",
            )
            chatPlayer(happy, "Alright, I'll go and see him.")
            say(PandemoniumSpeaker.STEVE, happy, "Jolly Roger!")
        }
        PandemoniumScene.JIM -> {
            say(PandemoniumSpeaker.JIM, quiz, "Can I help you, mate?")
            chatPlayer(quiz, "Are you Junior Jim?")
            say(PandemoniumSpeaker.JIM, quiz, "That's me. What do you need then?")
            chatPlayer(neutral, "Steve said you might be able to sort me out with a boat.")
            say(PandemoniumSpeaker.JIM, quiz, "You don't have a boat?")
            chatPlayer(neutral, "No.")
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Ha! We've got ourselves a Billy-no-boats! Classic! Yeah,<br>" +
                    "I can sort you out with a boat, but it's gonna cost you.",
            )
            chatPlayer(quiz, "How much?")
            say(PandemoniumSpeaker.JIM, happy, "Well, what do you have?")
            chatPlayer(
                confused,
                "Er... I do have this cup I found. Perhaps I could trade<br>" +
                    "it for a boat.",
            )
            say(PandemoniumSpeaker.JIM, happy, "Alright, let's have a gander at it.")
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Hmm... Yeah, yeah, I can work with this. This old rusty<br>" +
                    "cup will get you the finest raft I have!",
            )
            chatPlayer(
                confused,
                "Sorry, I think I must have misheard you. Did you say<br>" +
                    "raft?",
            )
            say(PandemoniumSpeaker.JIM, happy, "That's right! The best one I have!")
            chatPlayer(
                confused,
                "I was kind of hoping for a bit more than a raft. It's not<br>" +
                    "just the cup I have. I'm sure I can make up the funds<br>" +
                    "to buy something bigger.",
            )
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Sorry, mate, but even if you could, the raft is all I've<br>" +
                    "got to hand right now. Besides, you're gonna want to<br>" +
                    "build up some experience before you captain anything<br>" +
                    "too big. A raft will be perfect for you.",
            )
            chatPlayer(sad, "Okay...")
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Lighten up, mate. I'll have plenty more vessels in stock<br>" +
                    "soon. Come back once you've got a bit more know-how<br>" +
                    "and I'll be able to sort you right out.",
            )
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Now, about your raft... With that cup I'll be able to<br>" +
                    "sort you out with the finest helm, but it won't be<br>" +
                    "enough to cover the sails.",
            )
            chatPlayer(confused, "But I won't get anywhere without sails!")
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Don't panic, you silly plonker. I ain't about to send you<br>" +
                    "off on a raft with no sails. I'll sort them out for you,<br>" +
                    "but I'll just need a little favour in return.",
            )
            chatPlayer(quiz, "What favour?")
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "I've got some cargo I need picking up from Port Sarim<br>" +
                    "and bringing back here. In return for sorting you out<br>" +
                    "with some sails, I'll need you to go and collect it for<br>" +
                    "me.",
            )
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Oh, and I tell you what, I'll even set you up with the<br>" +
                    "materials to whack together a cargo hold. You won't be<br>" +
                    "shifting that cargo far without one.",
            )
            chatPlayer(happy, "Thanks!")
            say(PandemoniumSpeaker.JIM, happy, "Now, hand over that cup and we can get cracking.")
        }
        PandemoniumScene.JIM_SHIPYARD -> {
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Right, time to sort you out with a cargo hold. Let's<br>" +
                    "head on over to the shipyard...",
            )
        }
        PandemoniumScene.BUILD_INSTRUCTIONS -> {
            say(
                PandemoniumSpeaker.JIM,
                neutral,
                "Just use the gangplank over there to hop onto your<br>" +
                    "raft. Once you're on board, you can use your tools to<br>" +
                    "build a cargo hold. Don't worry about the materials,<br>" +
                    "mate. I'm supplying those this time.",
            )
        }
        PandemoniumScene.BUILD_FINISHED -> {
            say(
                PandemoniumSpeaker.JIM,
                neutral,
                "Very nice! Now, use the gangplank to hop back up here<br>" +
                    "and we can get going.",
            )
        }
        PandemoniumScene.CARGO_LOG -> {
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Okay, looks like that's you all sorted, mate. I'd say<br>" +
                    "you're now ready to set sail!",
            )
            chatPlayer(happy, "How exciting!")
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Why don't you take your new raft out for a spin? You<br>" +
                    "can head over to Port Sarim and collect that cargo for<br>" +
                    "me. Oh, which reminds me, now that you're officially a<br>" +
                    "captain, you'll need one of these.",
            )
        }
        PandemoniumScene.CARGO_ASSIGNMENT -> {
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Your very own captain's log! You can use that to keep<br>" +
                    "track of your adventures out at sea, including any<br>" +
                    "work you might end up taking on.",
            )
            chatPlayer(quiz, "Like collecting your cargo?")
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Right on, mate! In fact, I'll make a note of that in your<br>" +
                    "log now.",
            )
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "And that's you all ready to go, mate! Just hop on your<br>" +
                    "new raft when ready. If you need help finding my<br>" +
                    "cargo, just check in with the port master when you get<br>" +
                    "to Port Sarim.",
            )
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Now, unless you fancy a cheeky dip, the gangplank<br>" +
                    "down the docks will be your best bet for getting on<br>" +
                    "board.",
            )
        }
        PandemoniumScene.POST_DELIVERY -> {
            say(PandemoniumSpeaker.JIM, happy, "Alright, mate.")
            chatPlayer(neutral, "I collected your cargo and left it with the port master.")
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Very nice! That's us all squared up then. You ought to<br>" +
                    "pop back once you've got a bit more experience with<br>" +
                    "that raft. By then I should have some new vessels for<br>" +
                    "sale.",
            )
            chatPlayer(
                quiz,
                "I might just do that. Any tips on the best ways to get<br>" +
                    "that experience?",
            )
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "To be honest, mate, I'd say one of the best things<br>" +
                    "would be to take on more work like what you just did<br>" +
                    "for me. A lot of ports will have a notice board where<br>" +
                    "folks advertise jobs they want sorting.",
            )
            chatPlayer(quiz, "Sounds good. Anything else?")
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Well, now that you're officially a big cheese captain, you<br>" +
                    "might want to start charting the sea for your own<br>" +
                    "records. That log book I gave you has all sorts of<br>" +
                    "cracking tips on how to do so.",
            )
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "Oh, and while we're on about charting, I've also chucked<br>" +
                    "some useful bits and bobs in your cargo hold.",
            )
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "First up, I've left you a couple of repair kits in there.<br>" +
                    "Perfect for if you go being a numpty and end up<br>" +
                    "sailing straight into a proper big storm or a big group<br>" +
                    "of sharks.",
            )
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "The other thing in there is a spyglass. It's a proper<br>" +
                    "handy charting tool that's great for getting a good look<br>" +
                    "at things while out at sea.",
            )
            say(
                PandemoniumSpeaker.JIM,
                happy,
                "There's a chap called Charles who used to be pretty<br>" +
                    "good at the whole charting thing who might be able to<br>" +
                    "help you more. You can find him down in the cave on<br>" +
                    "the island.",
            )
            chatPlayer(
                happy,
                "Great! Thanks, Jim. I'd better go check in with Steve<br>" +
                    "now and see if Old Grog is back.",
            )
            say(PandemoniumSpeaker.JIM, happy, "Don't mention it, mate.")
        }
        PandemoniumScene.GROG -> {
            say(PandemoniumSpeaker.GROG, quiz, "Is this the one?")
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Aye! This Jolly Roger here is pretty keen to see some<br>" +
                    "justice dealt! Old Grog, meet... er...",
            )
            chatPlayer(neutral, "${player.displayName}.")
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "${player.displayName}! And ${player.displayName}, meet Old Grog.",
            )
            chatPlayer(happy, "Pleased to meet you.")
            say(
                PandemoniumSpeaker.GROG,
                neutral,
                "Steve tells me you're keen to see Will and Anne dealt<br>" +
                    "with. I'm open to sharing the bounty, but only if you<br>" +
                    "have what it takes. I have no time for layabouts.",
            )
            chatPlayer(happy, "I won't let you down!")
            say(
                PandemoniumSpeaker.GROG,
                neutral,
                "We'll see. I saw that raft you rocked up in. If that's all<br>" +
                    "you bring to the table, then you have some work to do.",
            )
            say(
                PandemoniumSpeaker.GROG,
                neutral,
                "It will take me some time to track Will and Anne down,<br>" +
                    "but make no mistake, I will find them. I expect you to<br>" +
                    "be ready by the time that happens. Don't be a<br>" +
                    "disappointment.",
            )
            chatPlayer(happy, "You have my word.")
            say(
                PandemoniumSpeaker.GROG,
                neutral,
                "Good. Now, that's me done drinking. Time to get to<br>" +
                    "work. I'll keep Steve updated on my progress, so make<br>" +
                    "sure you're checking in with him. He'll let you know<br>" +
                    "once I have something.",
            )
        }
        PandemoniumScene.COMPLETION -> {
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Yo ho ho, ${player.displayName}! Best you not waste time! Belay<br>" +
                    "your helm and abaft your rigging! Get out there and<br>" +
                    "make sure you're ready for Old Grog!",
            )
            chatPlayer(happy, "Will do!")
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "To help you on your way, I've got some pirate looty<br>" +
                    "here for you! Sawmill coupons! Useful if you want a top<br>" +
                    "vessel like mine.",
            )
            say(
                PandemoniumSpeaker.STEVE,
                happy,
                "Oh, and don't forget to pop back for a drink from time<br>" +
                    "to time. My rum is to die for! Now, avast!",
            )
        }
    }
}

private suspend fun Dialogue.say(
    speaker: PandemoniumSpeaker,
    expression: MesAnimType,
    text: String,
) {
    chatNpcSpecific(speaker.title, speaker.type, expression, text)
}

private enum class PandemoniumSpeaker(val title: String, val type: String) {
    WILL_SARIM("Will", "npc.sailing_intro_will_sarim"),
    WILL_BOAT("Will", "npc.sailing_intro_will_boat"),
    WILL_CUTSCENE("Will", "npc.sailing_intro_will_cutscene"),
    ANNE_SARIM("Anne", "npc.sailing_intro_anne_sarim"),
    ANNE_BOAT("Anne", "npc.sailing_intro_anne_boat"),
    ANNE_CUTSCENE("Anne", "npc.sailing_intro_anne_cutscene"),
    STEVE("'Squawking' Steve Beanie", "npc.steve_beanie"),
    RIBS("Ribs", "npc.sailing_intro_ribs"),
    JIM("Junior Jim", "npc.junior_jim"),
    GROG("Old Grog", "npc.sailing_intro_grog"),
}
