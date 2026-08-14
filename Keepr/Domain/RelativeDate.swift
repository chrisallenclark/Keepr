import Foundation

/// Short, human date strings used across lists.
///
/// Kept in one place so "3 days ago" reads the same on Today, a profile
/// timeline and a follow-up row.
enum RelativeDate {

    /// Past-leaning phrasing for timelines: "Today", "Yesterday", "3 days ago",
    /// "Aug 13", "Aug 13, 2024".
    static func past(
        _ date: Date,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> String {
        // Everything is measured against `now`. `Calendar.isDateInToday` and
        // friends compare against the system clock instead, which would quietly
        // ignore the caller's `now`.
        let days = dayOffset(from: date, to: now, calendar: calendar)

        if days == 0 { return "Today" }
        if days == 1 { return "Yesterday" }
        if days > 1, days < 7 { return "\(days) days ago" }
        return absolute(date, now: now, calendar: calendar)
    }

    /// Future-leaning phrasing for due dates: "Overdue", "Today", "Tomorrow",
    /// "Friday", "Sep 3".
    static func due(
        _ date: Date,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> String {
        let days = dayOffset(from: now, to: date, calendar: calendar)

        if days == 0 { return "Today" }
        if days == 1 { return "Tomorrow" }
        if days == -1 { return "Yesterday" }

        if days < 0 {
            let overdue = -days
            return overdue < 7 ? "\(overdue) days ago" : absolute(date, now: now, calendar: calendar)
        }
        if days < 7 { return date.formatted(.dateTime.weekday(.wide)) }
        return absolute(date, now: now, calendar: calendar)
    }

    /// Whole days between two instants, ignoring time of day.
    static func dayOffset(from start: Date, to end: Date, calendar: Calendar = .current) -> Int {
        calendar.dateComponents(
            [.day],
            from: calendar.startOfDay(for: start),
            to: calendar.startOfDay(for: end)
        ).day ?? 0
    }

    /// "Aug 13" this year, "Aug 13, 2024" otherwise.
    static func absolute(
        _ date: Date,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> String {
        let sameYear = calendar.component(.year, from: date) == calendar.component(.year, from: now)
        return sameYear
            ? date.formatted(.dateTime.month(.abbreviated).day())
            : date.formatted(.dateTime.month(.abbreviated).day().year())
    }

    /// "Last contact" line on a profile: "Last week", "3 weeks ago", "Never".
    static func lastContact(
        _ date: Date?,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> String {
        guard let date else { return "No interactions logged" }
        return past(date, now: now, calendar: calendar)
    }

    /// "Due Friday at 9:00 AM" style line for a follow-up with a set time.
    static func dueWithTime(
        _ followUp: FollowUp,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> String {
        let day = due(followUp.dueDate, now: now, calendar: calendar)
        guard followUp.hasTime else { return day }
        return "\(day) at \(followUp.dueDate.formatted(date: .omitted, time: .shortened))"
    }
}
