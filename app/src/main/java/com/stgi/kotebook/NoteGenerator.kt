package com.stgi.kotebook

import android.content.Context
import com.stgi.rodentia.BULLET_POINT_EMPTY
import com.stgi.rodentia.BULLET_POINT_FULL
import com.stgi.rodentia.palette
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random

class NoteGenerator(private val context: Context) {
    private val random = Random(Date().time)

    private val TITLES = arrayListOf(
        "",
        "Untitled",
        "Feel Good Inc.",
        "Death Stranding",
        "Mama",
        "Norman Reedus",
        "Baby dont hurt me",
        "Umbilical cord",
        "Deadman",
        "Pierre DeLavigne",
        "Keep it off the books",
        "Esquirel",
        "Top Secret"
    )

    private val TEXTS = arrayListOf(
        "Rodents (from Latin Rodere, \"to gnaw\") are mammals of the order Rodentia, which are characterized by a single pair of continuously growing incisors in each of the upper and lower jaws. About 40% of all mammal species are rodents (2,277 species); they are found in vast numbers on all continents except Antarctica. They are the most diversified mammalian order and live in a variety of terrestrial habitats, including human-made environments.",
        "Lista:" + BULLET_POINT_FULL + "questo è segnato\n" + BULLET_POINT_EMPTY + "questo no",
        BULLET_POINT_FULL + "Kill Steve\n" + BULLET_POINT_EMPTY + "Kill Mary" + BULLET_POINT_EMPTY + "Kill Jacob" + BULLET_POINT_FULL + "Kill Bernie",
        "The distinguishing feature of the rodents is their pairs of continuously growing, razor-sharp, open-rooted incisors.[1] These incisors have thick layers of enamel on the front and little enamel on the back.[2] Because they do not stop growing, the animal must continue to wear them down so that they do not reach and pierce the skull. As the incisors grind against each other, the softer dentine on the rear of the teeth wears away, leaving the sharp enamel edge shaped like the blade of a chisel.",
        "In many species, the molars are relatively large, intricately structured, and highly cusped or ridged. Rodent molars are well equipped to grind food into small particles.[1] The jaw musculature is strong. The lower jaw is thrust forward while gnawing and is pulled backwards during chewing.",
        "While the largest species, the capybara, can weigh as much as 66 kg (146 lb), most rodents weigh less than 100 g (3.5 oz). The smallest rodent is the Baluchistan pygmy jerboa, which averages only 4.4 cm (1.7 in) in head and body length, with adult females weighing only 3.75 g (0.132 oz). Rodents have wide-ranging morphologies, but typically have squat bodies and short limbs.[1] The fore limbs usually have five digits, including an opposable thumb, while the hind limbs have three to five digits. The elbow gives the forearms great flexibility.[3][9] The majority of species are plantigrade, walking on both the palms and soles of their feet, and have claw-like nails. The nails of burrowing species tend to be long and strong, while arboreal rodents have shorter, sharper nails.[9] Rodent species use a wide variety of methods of locomotion including quadrupedal walking, running, burrowing, climbing, bipedal hopping (kangaroo rats and hopping mice), swimming and even gliding.[3] Scaly-tailed squirrels and flying squirrels, although not closely related, can both glide from tree to tree using parachute-like membranes that stretch from the fore to the hind limbs.",
        "One of the most widespread groups of mammals, rodents can be found on every continent except Antarctica. They are the only terrestrial placental mammals to have colonized Australia and New Guinea without human intervention. Humans have also allowed the animals to spread to many remote oceanic islands (e.g., the Polynesian rat).[3] Rodents have adapted to almost every terrestrial habitat, from cold tundra (where they can live under snow) to hot deserts. ",
        "Some species such as tree squirrels and New World porcupines are arboreal, while some, such as gophers, tuco-tucos, and mole rats, live almost completely underground, where they build complex burrow systems." + BULLET_POINT_FULL + "Yep",
        BULLET_POINT_FULL + "Get eggs\n" + BULLET_POINT_FULL + "Crack one" + BULLET_POINT_EMPTY + "Crack another" + BULLET_POINT_EMPTY + "Put them in a pan" + BULLET_POINT_EMPTY + "Fry them" + BULLET_POINT_EMPTY + "Enjoy eggs",
        "The Texas pocket gopher avoids emerging onto the surface to feed by seizing the roots of plants with its jaws and pulling them downwards into its burrow. It also practices coprophagy.[22] The African pouched rat forages on the surface, gathering anything that might be edible into its capacious cheek pouches until its face bulges out sideways. It then returns to its burrow to sort through the material it has gathered and eats the nutritious items.",
        "House mouse",
        "Scemochilegge",
        "Watch Youtube videos"
    )

    fun generateNotes(lastVersionCode: Int): Array<Note.NoteData> =
        if (BuildConfig.DEBUG) {
            arrayOf(
                generateNote(),
                generateNote(),
                generateNote(),
                generateNote(),
                generateNote(),
                generateNote(),
                generateNote()
            )
        } else {
            val list = ArrayList<Note.NoteData>()
            when {
                lastVersionCode < AGOUTI -> {
                    list.add(generateNote(
                        title = context.getString(R.string.welcome_title),
                        text = context.getString(R.string.welcome_text),
                        isRecording = false,
                        isPinned = true,
                        color = context.resources.getColor(R.color.noteColor2, context.theme)
                    ))
                }
                lastVersionCode < BEAVER -> {

                }
                lastVersionCode < CAVIA -> {

                }
                lastVersionCode < DORMOUSE -> {

                }
                lastVersionCode < ESQUIREL -> {

                }
                lastVersionCode < FLYING_SQUIRREL -> {

                }
            }
            list.toArray(arrayOfNulls<Note.NoteData>(list.size))
        }

    private fun generateNote(
        isRecording: Boolean = random.nextBoolean(),
        title: String = TITLES[random.nextInt(0, TITLES.size)],
        text: String = if (isRecording) "$title.aar" else TEXTS[random.nextInt(0, TEXTS.size)],
        color: Int = generateRandomColor(),
        isPinned: Boolean = random.nextBoolean()
    ): Note.NoteData = Note.NoteData(title = title, text = text, color = color, isRecording = isRecording, pinned = isPinned)

    fun generateRandomColor() : Int {
        return context.resources.getColor(
            palette[random.nextInt(palette.size)], context.theme)
    }
}