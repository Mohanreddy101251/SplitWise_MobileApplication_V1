package com.spliwise.classify

data class CatDef(val id: String, val name: String, val emoji: String, val color: Long)

object Categories {
    const val UNKNOWN = "unknown"
    const val INCOME = "income"
    const val FRIENDS = "friends"
    const val OTHERS = "others"
    const val HOME = "home"
    /** Paying a credit-card bill, or a refund/payment credited back to the card: moves money, not income or spend. */
    const val CARDPAY = "cardpay"
    val TRANSFERS = setOf(CARDPAY)

    val defaults = listOf(
        CatDef("food", "Food", "🍔", 0xFFFF6B6B),
        CatDef("grocery", "Grocery", "🛒", 0xFF1FC8A9),
        CatDef("travel", "Travel", "🚕", 0xFF3FA9FF),
        CatDef("petrol", "Petrol", "⛽", 0xFFFF9F1C),
        CatDef("shopping", "Shopping", "🛍️", 0xFFB15CFF),
        CatDef("salon", "Salon", "💇", 0xFFFF77C8),
        CatDef("gym", "Gym", "🏋️", 0xFFFF5D8F),
        CatDef(FRIENDS, "Friend transfers", "🤝", 0xFF00B4D8),
        CatDef("invest", "Investments", "📈", 0xFF06D6A0),
        CatDef("bills", "Bills & Utilities", "💡", 0xFFFFC233),
        CatDef("rent", "Rent", "🏠", 0xFF8E7DFF),
        CatDef(HOME, "Home", "🏡", 0xFFFF8A65),
        CatDef("health", "Health", "💊", 0xFFEF476F),
        CatDef("fun", "Entertainment", "🎬", 0xFF7B2CBF),
        CatDef("education", "Education", "📚", 0xFF4361EE),
        CatDef("emi", "EMI & Loans", "🏦", 0xFF5C677D),
        CatDef(CARDPAY, "Card bill & refunds", "💳", 0xFF7E8CA3),
        CatDef("insurance", "Insurance", "🛡️", 0xFF118AB2),
        CatDef("gifts", "Gifts", "🎁", 0xFFE76F51),
        CatDef("atm", "ATM Cash", "🏧", 0xFF5E60CE),
        CatDef(INCOME, "Income", "💰", 0xFF19B37D),
        CatDef(OTHERS, "Others", "✨", 0xFF9AA0A6),
        CatDef(UNKNOWN, "Needs review", "❓", 0xFFB8B5C9),
    )

    /** Spoken words -> category id (used by the voice parser). */
    val voiceKeywords: Map<String, String> = buildMap {
        fun kw(cat: String, vararg w: String) = w.forEach { this[it] = cat }
        kw("food", "food", "lunch", "dinner", "breakfast", "snacks", "snack", "tea", "coffee", "chai", "restaurant",
            "biryani", "swiggy", "zomato", "pizza", "burger", "juice", "meal", "meals", "tiffin")
        kw("grocery", "grocery", "groceries", "vegetables", "vegetable", "veggies", "milk", "fruits", "fruit",
            "kirana", "provisions", "supermarket", "dmart", "eggs", "kpn")
        kw("travel", "travel", "cab", "taxi", "auto", "uber", "ola", "rapido", "bus", "train", "metro", "flight",
            "ticket", "tickets", "parking", "toll")
        kw("petrol", "petrol", "fuel", "diesel")
        kw("shopping", "shopping", "clothes", "dress", "shoes", "amazon", "flipkart", "myntra", "electronics", "ajio")
        kw("salon", "salon", "saloon", "haircut", "hair cut", "barber", "parlour", "parlor", "spa", "beard")
        kw("gym", "gym", "workout", "fitness", "protein")
        kw(FRIENDS, "friend transfer", "transfer", "transferred")
        kw("invest", "investment", "investments", "invest", "sip", "mutual fund", "mutual funds", "stocks", "stock",
            "gold", "zerodha", "groww")
        kw("bills", "bill", "bills", "electricity", "current bill", "power bill", "water bill", "recharge",
            "mobile recharge", "wifi", "internet", "broadband", "dth", "gas bill", "cylinder")
        kw("rent", "rent", "house rent")
        kw(HOME, "home", "household", "furniture", "utensils", "repair", "repairs", "plumber", "electrician",
            "cleaning", "maid", "home items")
        kw("health", "medicine", "medicines", "doctor", "hospital", "pharmacy", "medical", "tablets", "clinic", "health")
        kw("fun", "movie", "movies", "netflix", "entertainment", "subscription", "hotstar", "spotify", "games", "outing")
        kw("education", "books", "book", "course", "fees", "fee", "tuition", "school", "college", "education")
        kw("emi", "emi", "loan")
        kw(CARDPAY, "credit card", "credit card bill", "card bill", "card payment")
        kw("insurance", "insurance", "premium", "policy")
        kw("gifts", "gift", "gifts", "donation", "temple")
        kw("atm", "atm", "cash withdrawal", "withdrawal")
        kw(INCOME, "salary", "income", "received", "refund", "cashback")
        kw(OTHERS, "others", "other", "misc", "miscellaneous")
    }

    /**
     * Bank-SMS merchant keywords -> category. Checked in order, first hit wins.
     * Short keywords (<5 chars) must match a whole word; longer ones may match inside a word.
     */
    val merchantDict: List<Pair<String, String>> = buildList {
        fun kw(cat: String, vararg w: String) = w.forEach { add(it to cat) }
        kw("atm", "atm", "cash wdl", "atm wdl", "cash withdrawal", "nfs", "awb")
        kw(INCOME, "salary", "sal", "interest", "int pd", "dividend", "refund", "cashback", "reversal")
        kw("grocery", "kpn", "kpn fresh", "dmart", "d mart", "avenue supermarts", "more retail", "more supermarket",
            "reliance fresh", "reliance smart", "smart bazaar", "star bazaar", "spar", "ratnadeep", "nilgiris",
            "bigbasket", "big basket", "blinkit", "zepto", "instamart", "jiomart", "natures basket", "heritage fresh",
            "namdhari", "metro cash", "supermarket", "kirana", "provision", "vegetables", "fruits", "nandini", "dairy")
        kw("petrol", "petrol", "petroleum", "fuel", "fuels", "filling station", "hpcl", "hp pay", "iocl",
            "indian oil", "bpcl", "bharat petroleum", "shell", "nayara", "jio bp", "reliance bp", "essar oil",
            "service station")
        kw("food", "swiggy", "zomato", "eatsure", "dominos", "pizza hut", "mcdonald", "kfc", "burger king", "subway",
            "starbucks", "chaayos", "cafe coffee day", "ccd", "restaurant", "hotel", "cafe", "bakery", "biryani",
            "darshini", "tiffin", "bakes", "juice", "sweets", "chai")
        kw("travel", "uber", "ola", "olacabs", "rapido", "namma yatri", "irctc", "redbus", "makemytrip", "goibibo",
            "cleartrip", "ixigo", "indigo", "interglobe", "air india", "akasa", "bmtc", "ksrtc", "bmrcl", "metro",
            "fastag", "toll", "parking", "yulu")
        kw("shopping", "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "croma",
            "reliance digital", "lifestyle", "westside", "max fashion", "pantaloons", "zudio", "decathlon", "ikea",
            "reliance trends", "lenskart", "firstcry")
        kw("salon", "salon", "saloon", "naturals", "green trends", "toni guy", "lakme", "barber", "beauty", "parlour",
            "spa", "looks")
        kw("gym", "gym", "fitness", "cult fit", "cultfit", "curefit", "anytime fitness", "crossfit")
        kw("invest", "zerodha", "groww", "upstox", "angel one", "angelone", "kuvera", "indian clearing", "iccl",
            "nse clearing", "bse", "mutual fund", "mf", "sip", "smallcase", "paytm money", "et money", "scripbox",
            "nps", "ppf", "digital gold", "safegold", "mmtc")
        kw("bills", "bescom", "electricity", "bwssb", "airtel", "jio", "vodafone", "vi", "bsnl", "act fibernet",
            "hathway", "tata play", "tatasky", "dish tv", "recharge", "broadband", "indane", "bharatgas", "hp gas",
            "piped gas", "gas", "postpaid", "prepaid", "billdesk", "bbps")
        kw("rent", "rent", "nobroker", "nestaway", "landlord", "pg")
        kw(HOME, "home centre", "homecentre", "pepperfry", "urban company", "urbanclap", "wakefit", "sleepwell",
            "furniture", "hardware", "sanitary", "electricals", "plywood", "home needs")
        kw("health", "pharmacy", "medical", "medicals", "medplus", "apollo", "netmeds", "1mg", "pharmeasy",
            "hospital", "clinic", "diagnostic", "labs", "healthcare", "dental", "practo")
        kw("fun", "netflix", "prime video", "hotstar", "disney", "spotify", "youtube", "bookmyshow", "pvr", "inox",
            "cinepolis", "jiocinema", "zee5", "sonyliv", "google play", "playstation", "steam")
        kw("education", "udemy", "coursera", "byju", "unacademy", "school", "college", "university", "academy",
            "tuition", "institute")
        kw(CARDPAY, "credit card", "cc payment", "card payment", "cred club", "cred")
        kw("emi", "emi", "loan", "bajaj finance", "bajaj finserv", "home credit", "nach", "ach d")
        kw("insurance", "insurance", "lic", "policybazaar", "acko", "hdfc ergo", "icici lombard", "star health",
            "max life", "tata aia", "sbi life")
    }
}
