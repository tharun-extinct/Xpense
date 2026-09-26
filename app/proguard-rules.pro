# Keep rules for the release build.
#
# R8 runs in full mode, repackages every class into the unnamed package, and widens member
# visibility to inline more aggressively. None of that is configured here: all three are AGP 9.1
# defaults, and the only way to get them wrong is to switch them off. See the release-optimization
# rules in blueprints/build-and-ci.md.
#
# Everything below marks a place where R8's reachability analysis is *correct about the bytecode
# and wrong about the app*, because something is reached by name at runtime. Each rule costs DEX
# size, so a rule with no runtime lookup behind it should be deleted rather than kept "to be safe".

# -----------------------------------------------------------------------------
# Enum constant names are persisted user data.
# -----------------------------------------------------------------------------
# Converters.kt round-trips TransactionDirection, TransactionState and RuleSource through SQLite
# with valueOf()/name(). A renamed constant does not make the app smaller in any way the user
# benefits from - it makes every row written by a previous install unreadable, and the failure
# surfaces as IllegalArgumentException on the first query after an update, with the original
# message long gone (architecture.md #error-and-recovery).
#
# <fields> is the load-bearing part: it pins the constant names themselves. Keeping only
# values()/valueOf() preserves the lookup machinery while still allowing the things it looks up
# to be renamed.
#
# Scoped to every enum in the app rather than to the three that are persisted today, because the
# cost is a handful of retained field names and the cost of forgetting to extend a narrower rule is
# an unreadable database.
-keepclassmembers enum dev.expensetracker.app.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -----------------------------------------------------------------------------
# WorkManager instantiates workers reflectively from a class name it stored earlier.
# -----------------------------------------------------------------------------
# The name is written into WorkManager's own database at enqueue time and read back when the work
# actually runs, which can be after an app update - i.e. after a *different* R8 run that may have
# handed that same short name to some other class. Pinning the names makes the stored string mean
# the same thing across versions. androidx.work ships consumer rules of its own; these are stated
# explicitly because a library's rules are not something this repo can verify, and the failure
# mode is silent (the worker never runs, so SMS just stops being ingested).
-keep class dev.expensetracker.app.ingestion.ProcessSmsWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class dev.expensetracker.app.ingestion.SmsBackfillWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# -----------------------------------------------------------------------------
# Room resolves its generated implementation by string concatenation.
# -----------------------------------------------------------------------------
# Room looks up "<database class name>_Impl", so the two names have to be renamed together or not
# at all. Only the class names are kept; members stay fully shrinkable.
-keep class dev.expensetracker.app.data.AppDatabase
-keep class dev.expensetracker.app.data.AppDatabase_Impl

# -----------------------------------------------------------------------------
# Deliberately absent: kotlinx.serialization keep rules.
# -----------------------------------------------------------------------------
# The usual -keep block for @Serializable classes exists because `Json.decodeFromString<T>(...)`
# resolves a serializer reflectively. Both call sites here (SeedRuleLoader, TransactionParser)
# pass an explicit `X.serializer()`, which is a direct static reference R8 can trace, and no
# serializable class in this app has an enum or polymorphic field. JSON property names come from
# the descriptor built at compile time from string literals, so obfuscation cannot change them.
# If a reified decodeFromString ever appears, this paragraph stops being true.

# -----------------------------------------------------------------------------
# Deliberately absent: resource keep rules.
# -----------------------------------------------------------------------------
# Optimized resource shrinking runs in safe mode, which preserves resources that look like they
# might be reached via Resources.getIdentifier(). The app calls it nowhere, so there is nothing to
# exempt; adding tools:shrinkMode="strict" would buy a little more size at the cost of making that
# claim load-bearing.
