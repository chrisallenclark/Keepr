import Foundation
import SwiftData

/// Realistic development data so every screen can be evaluated without building
/// a contact database by hand.
///
/// Everything created here is flagged `isSampleData`, so `remove(from:)` can
/// take it all back out without touching a single real record.
enum SampleData {

    @MainActor
    static func isLoaded(in context: ModelContext) -> Bool {
        let descriptor = FetchDescriptor<Person>()
        let people = (try? context.fetch(descriptor)) ?? []
        return people.contains(where: \.isSampleData)
    }

    @MainActor
    static func remove(from context: ModelContext) {
        let people = (try? context.fetch(FetchDescriptor<Person>())) ?? []
        for person in people where person.isSampleData {
            context.delete(person)
        }
        let groups = (try? context.fetch(FetchDescriptor<PersonGroup>())) ?? []
        for group in groups where group.isSampleData {
            context.delete(group)
        }
        try? context.save()
    }

    /// Inserts the sample set. Safe to call twice — it no-ops if already loaded.
    @MainActor
    static func load(into context: ModelContext, now: Date = Date()) {
        guard !isLoaded(in: context) else { return }
        KeeprStore.seedIfNeeded(context)

        let calendar = Calendar.current
        func daysAgo(_ days: Int) -> Date {
            calendar.date(byAdding: .day, value: -days, to: now) ?? now
        }
        func daysAhead(_ days: Int) -> Date {
            calendar.startOfDay(for: calendar.date(byAdding: .day, value: days, to: now) ?? now)
        }
        func tag(_ name: String, _ kind: TagKind) -> RelationshipTag {
            KeeprStore.tag(named: name, kind: kind, in: context)
        }

        // MARK: Business — the potential client from the gym

        let jake = Person(
            givenName: "Jake",
            familyName: "Martinez",
            context: .business,
            company: "Martinez Roofing",
            jobTitle: "Owner",
            phoneNumbers: ["(561) 555-0142"],
            emailAddresses: ["jake@martinezroofing.com"],
            priority: .high,
            howWeMet: "Met at Life Time on the gym floor",
            createdAt: daysAgo(6),
            isSampleData: true
        )
        context.insert(jake)
        jake.tags = [tag("Potential Client", .business), tag("Lead", .business)]
        jake.workNote = "Runs a roofing crew of nine. Knows every contractor in Delray."

        let jakeChat = interaction(
            .inPerson,
            at: daysAgo(1),
            title: "Talked about training at Life Time",
            raw: "Ran into Jake at Life Time. He owns a roofing company, wants to lose about "
                + "20 pounds before his daughter's wedding, and said to text him next week "
                + "about getting started.",
            summary: "Wants to start training. Text next week.",
            for: jake,
            in: context
        )
        addMemories(
            [
                ("Owns Martinez Roofing", .work, .high),
                ("Wants to lose 20 lb", .goals, .high),
                ("Daughter is getting married in the spring", .family, .normal),
                ("Trains at Life Time in the mornings", .personal, .normal)
            ],
            to: jake,
            from: jakeChat,
            at: daysAgo(1),
            in: context
        )
        addFollowUp(
            "Text about starting training",
            note: "He asked me to reach out after his vacation.",
            due: daysAhead(2),
            priority: .high,
            for: jake,
            from: jakeChat,
            in: context
        )

        // MARK: Business — the active client

        let sarah = Person(
            givenName: "Sarah",
            familyName: "Miller",
            context: .business,
            company: "Coastal Design Co.",
            jobTitle: "Creative Director",
            phoneNumbers: ["(561) 555-0198"],
            emailAddresses: ["sarah.miller@coastaldesign.com"],
            priority: .high,
            isFavorite: true,
            howWeMet: "Referred by Michael Reed",
            introducedBy: "Michael Reed",
            createdAt: daysAgo(240),
            isSampleData: true
        )
        context.insert(sarah)
        sarah.tags = [tag("Current Client", .business)]

        let sarahCall = interaction(
            .call,
            at: daysAgo(3),
            title: "Weekly check-in",
            raw: "Good week — down 2 lb. Traveling to Italy in September so we need to "
                + "front-load the next block.",
            summary: "Down 2 lb. Italy trip in September — adjust the plan around it.",
            for: sarah,
            in: context
        )
        addMemories(
            [
                ("Going to Italy in September", .importantDate, .high),
                ("Trains Tuesday and Thursday mornings", .preferences, .normal),
                ("Two kids, 7 and 10", .family, .normal)
            ],
            to: sarah,
            from: sarahCall,
            at: daysAgo(3),
            in: context
        )
        _ = interaction(
            .inPerson,
            at: daysAgo(10),
            title: "Session",
            raw: nil,
            summary: "Reworked her squat setup. Much better depth.",
            for: sarah,
            in: context
        )
        addFollowUp(
            "Send the September training block",
            due: daysAgo(1),
            priority: .high,
            for: sarah,
            from: sarahCall,
            in: context
        )

        // MARK: Business — the partner who sends referrals

        let michael = Person(
            givenName: "Michael",
            familyName: "Reed",
            context: .both,
            company: "Reed Physical Therapy",
            jobTitle: "Partner",
            phoneNumbers: ["(561) 555-0110"],
            emailAddresses: ["michael@reedpt.com"],
            priority: .normal,
            isFavorite: true,
            howWeMet: "Shared office space in 2021",
            createdAt: daysAgo(900),
            isSampleData: true
        )
        context.insert(michael)
        michael.tags = [
            tag("Business Partner", .business),
            tag("Referral Source", .business),
            tag("Friend", .personal)
        ]

        let michaelMeeting = interaction(
            .meeting,
            at: daysAgo(14),
            title: "Quarterly catch-up",
            raw: nil,
            summary: "Agreed to keep swapping referrals. He wants to co-host a workshop in the fall.",
            for: michael,
            in: context
        )
        addMemories(
            [
                ("Sends 2–3 referrals a month", .business, .high),
                ("Wants to co-host a fall workshop", .goals, .normal),
                ("Runs the Delray half marathon every year", .interests, .low)
            ],
            to: michael,
            from: michaelMeeting,
            at: daysAgo(14),
            in: context
        )
        addFollowUp(
            "Pick a date for the workshop",
            due: daysAhead(5),
            for: michael,
            from: michaelMeeting,
            in: context
        )

        // MARK: Business — the lead that went quiet

        let dana = Person(
            givenName: "Dana",
            familyName: "Whitfield",
            context: .business,
            company: "Whitfield Interiors",
            jobTitle: "Principal",
            phoneNumbers: ["(561) 555-0177"],
            priority: .normal,
            howWeMet: "Chamber of commerce mixer",
            createdAt: daysAgo(90),
            isSampleData: true
        )
        context.insert(dana)
        dana.tags = [tag("Potential Client", .business)]
        _ = interaction(
            .email,
            at: daysAgo(38),
            title: "Sent pricing",
            raw: nil,
            summary: "Sent the small-group pricing sheet. She said she'd think it over.",
            for: dana,
            in: context
        )
        addMemories(
            [("Considering small-group training for her team", .business, .normal)],
            to: dana,
            from: nil,
            at: daysAgo(38),
            in: context
        )

        // MARK: Business — the vendor

        let priya = Person(
            givenName: "Priya",
            familyName: "Raman",
            context: .business,
            company: "Bright Studio",
            jobTitle: "Photographer",
            emailAddresses: ["hello@brightstudio.co"],
            createdAt: daysAgo(150),
            isSampleData: true
        )
        context.insert(priya)
        priya.tags = [tag("Vendor", .business)]
        _ = interaction(
            .email,
            at: daysAgo(21),
            title: "Booked the shoot",
            raw: nil,
            summary: "Booked her for new headshots. Invoice paid.",
            for: priya,
            in: context
        )

        // MARK: Personal — close friend

        let alex = Person(
            givenName: "Alex",
            familyName: "Nguyen",
            context: .personal,
            phoneNumbers: ["(561) 555-0155"],
            isFavorite: true,
            createdAt: daysAgo(1500),
            isSampleData: true
        )
        context.insert(alex)
        alex.tags = [tag("Close Friend", .personal)]
        let alexText = interaction(
            .text,
            at: daysAgo(2),
            title: nil,
            raw: nil,
            summary: "Made plans for Saturday. He starts the new job on the 1st.",
            for: alex,
            in: context
        )
        addMemories(
            [
                ("Starting a new job on the 1st", .work, .high),
                ("Allergic to shellfish", .preferences, .high)
            ],
            to: alex,
            from: alexText,
            at: daysAgo(2),
            in: context
        )

        // MARK: Personal — family

        let mom = Person(
            givenName: "Linda",
            familyName: "Clark",
            preferredName: "Mom",
            context: .personal,
            phoneNumbers: ["(407) 555-0121"],
            priority: .high,
            isFavorite: true,
            createdAt: daysAgo(2000),
            isSampleData: true
        )
        context.insert(mom)
        mom.tags = [tag("Family", .personal)]
        _ = interaction(
            .call,
            at: daysAgo(5),
            title: "Sunday call",
            raw: nil,
            summary: "Her knee is better. Wants to visit in October.",
            for: mom,
            in: context
        )
        addMemories(
            [
                ("Wants to visit in October", .importantDate, .high),
                ("Birthday: March 14", .importantDate, .high)
            ],
            to: mom,
            from: nil,
            at: daysAgo(5),
            in: context
        )
        addFollowUp(
            "Call about October flights",
            due: daysAhead(4),
            for: mom,
            from: nil,
            in: context
        )

        // MARK: Personal — friend who has gone quiet

        let ben = Person(
            givenName: "Ben",
            familyName: "Ortiz",
            context: .personal,
            priority: .high,
            createdAt: daysAgo(800),
            isSampleData: true
        )
        context.insert(ben)
        ben.tags = [tag("Friend", .personal)]
        _ = interaction(
            .inPerson,
            at: daysAgo(95),
            title: "Dinner",
            raw: nil,
            summary: "Caught up over dinner. He and Maya are house hunting.",
            for: ben,
            in: context
        )
        addMemories(
            [("House hunting with Maya", .personal, .normal)],
            to: ben,
            from: nil,
            at: daysAgo(95),
            in: context
        )

        // Places: one gym holding a client, a colleague and a friend at once,
        // plus the clients trained at home. This is the pair of chips the People
        // screen is built around, so the sample set has to show it working.
        let lifeTime = PersonGroup(
            name: "Life Time",
            symbolName: "figure.run",
            detail: "Delray",
            aliases: "LT",
            context: .both,
            sortOrder: 10,
            isSampleData: true
        )
        context.insert(lifeTime)
        lifeTime.members = [jake, michael, alex]

        let homeStudio = PersonGroup(
            name: "Home Studio",
            symbolName: "house",
            aliases: "HS, In-Home",
            context: .business,
            sortOrder: 20,
            isSampleData: true
        )
        context.insert(homeStudio)
        homeStudio.members = [sarah, dana]

        // The referral that started the client relationship, as a link rather
        // than a note — it reads from both ends and survives a rename.
        context.insert(
            PersonLink(
                personA: sarah,
                personB: michael,
                labelAToB: "Referred By",
                labelBToA: "Referred",
                note: "Sent her over after her half marathon",
                createdAt: daysAgo(240)
            )
        )

        try? context.save()
    }

    // MARK: - Helpers

    @MainActor
    @discardableResult
    private static func interaction(
        _ kind: InteractionKind,
        at date: Date,
        title: String?,
        raw: String?,
        summary: String?,
        for person: Person,
        in context: ModelContext
    ) -> Interaction {
        let interaction = Interaction(
            kind: kind,
            occurredAt: date,
            title: title,
            rawNote: raw,
            summary: summary,
            person: person
        )
        context.insert(interaction)
        if person.lastInteractionAt == nil || (person.lastInteractionAt ?? .distantPast) < date {
            person.lastInteractionAt = date
        }
        return interaction
    }

    @MainActor
    private static func addMemories(
        _ items: [(String, MemoryCategory, Priority)],
        to person: Person,
        from interaction: Interaction?,
        at date: Date,
        in context: ModelContext
    ) {
        for item in items {
            context.insert(
                Memory(
                    content: item.0,
                    category: item.1,
                    importance: item.2,
                    createdAt: date,
                    person: person,
                    sourceInteraction: interaction
                )
            )
        }
    }

    @MainActor
    private static func addFollowUp(
        _ title: String,
        note: String? = nil,
        due: Date,
        priority: Priority = .normal,
        for person: Person,
        from interaction: Interaction?,
        in context: ModelContext
    ) {
        context.insert(
            FollowUp(
                title: title,
                note: note,
                dueDate: due,
                priority: priority,
                person: person,
                sourceInteraction: interaction
            )
        )
    }
}

// MARK: - Previews

extension ModelContainer {

    /// An in-memory container preloaded with the sample set, for SwiftUI previews.
    @MainActor
    static var preview: ModelContainer = {
        let container = KeeprStore.makeContainer(inMemory: true)
        SampleData.load(into: container.mainContext)
        return container
    }()
}
