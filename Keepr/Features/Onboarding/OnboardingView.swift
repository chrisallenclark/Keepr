import SwiftData
import SwiftUI

/// Three screens: what this is, which side you're starting on, and your people.
///
/// Contact access is requested only on the screen that explains why, and
/// "Not Now" is a real option — the app works without it.
struct OnboardingView: View {

    @Binding var mode: ContextMode
    let onFinish: () -> Void

    @Environment(\.contactStore) private var contactStore
    @Environment(\.modelContext) private var context

    private enum Step: Int, CaseIterable {
        case welcome
        case context
        case contacts
    }

    @State private var step: Step = .welcome
    @State private var access: ContactAccess = .notDetermined
    @State private var isImporting = false
    @State private var importedCount = 0
    @State private var isRequesting = false

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)

            Group {
                switch step {
                case .welcome: welcome
                case .context: contextChoice
                case .contacts: contacts
                }
            }
            .frame(maxWidth: 420)
            .padding(.horizontal, Theme.Spacing.section)

            Spacer(minLength: 0)

            footer
                .padding(.horizontal, Theme.Spacing.section)
                .padding(.bottom, Theme.Spacing.large)
        }
        .background(Color(.systemBackground))
        .sheet(isPresented: $isImporting) {
            ContactImportView(mode: mode) { count in
                importedCount = count
            }
        }
        .task { access = contactStore.authorizationStatus() }
    }

    // MARK: - Screens

    private var welcome: some View {
        VStack(spacing: Theme.Spacing.large) {
            Image(systemName: "person.2.circle")
                .font(.system(size: 56, weight: .light))
                .foregroundStyle(Color.accentColor)

            VStack(spacing: Theme.Spacing.medium) {
                Text("Keepr")
                    .font(.largeTitle.weight(.semibold))

                Text("Your business relationships and your personal ones, finally kept apart — and remembered properly.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
    }

    private var contextChoice: some View {
        VStack(spacing: Theme.Spacing.large) {
            Image(systemName: "rectangle.righthalf.inset.filled")
                .font(.system(size: 48, weight: .light))
                .foregroundStyle(Color.accentColor)

            VStack(spacing: Theme.Spacing.medium) {
                Text("One switch")
                    .font(.title.weight(.semibold))

                Text("Every screen shows one side of your life at a time. Someone who's both a friend and a client is still one person — they just show up in both.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            VStack(spacing: Theme.Spacing.small) {
                Picker("Start in", selection: $mode) {
                    ForEach(ContextMode.allCases) { option in
                        Text(option.title).tag(option)
                    }
                }
                .pickerStyle(.segmented)

                Text("You can switch any time.")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
            .padding(.top, Theme.Spacing.small)
        }
    }

    private var contacts: some View {
        VStack(spacing: Theme.Spacing.large) {
            Image(systemName: "person.crop.circle.badge.plus")
                .font(.system(size: 48, weight: .light))
                .foregroundStyle(Color.accentColor)

            VStack(spacing: Theme.Spacing.medium) {
                Text("Bring in your people")
                    .font(.title.weight(.semibold))

                Text("Keepr can link relationships to your contacts so you can call, text or email in one tap. Your contacts stay on your device — nothing is uploaded and nothing is written back.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            if importedCount > 0 {
                Label("\(importedCount) added", systemImage: "checkmark.circle.fill")
                    .font(.subheadline)
                    .foregroundStyle(.green)
            } else if access == .denied || access == .restricted {
                Text("No problem — you can add people by hand, and turn contacts on later in Settings.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
    }

    // MARK: - Footer

    @ViewBuilder
    private var footer: some View {
        VStack(spacing: Theme.Spacing.medium) {
            switch step {
            case .welcome, .context:
                primaryButton("Continue") {
                    withAnimation { step = Step(rawValue: step.rawValue + 1) ?? .contacts }
                }
            case .contacts:
                if access.canRead {
                    primaryButton(importedCount > 0 ? "Done" : "Choose Contacts") {
                        if importedCount > 0 {
                            finish()
                        } else {
                            isImporting = true
                        }
                    }
                    Button("Skip for now", action: finish)
                        .font(.subheadline)
                } else if access == .notDetermined {
                    primaryButton(isRequesting ? "Asking…" : "Allow Contacts") {
                        Task { await requestAccess() }
                    }
                    .disabled(isRequesting)
                    Button("Not Now", action: finish)
                        .font(.subheadline)
                } else {
                    primaryButton("Get Started", action: finish)
                }
            }

            pageIndicator
        }
    }

    private func primaryButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.headline)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
        }
        .buttonStyle(.borderedProminent)
    }

    private var pageIndicator: some View {
        HStack(spacing: 6) {
            ForEach(Step.allCases, id: \.rawValue) { page in
                Circle()
                    .fill(page == step ? Color.accentColor : Color(.tertiaryLabel))
                    .frame(width: 6, height: 6)
            }
        }
        .padding(.top, Theme.Spacing.tight)
        .accessibilityHidden(true)
    }

    // MARK: - Actions

    private func requestAccess() async {
        isRequesting = true
        access = await contactStore.requestAccess()
        isRequesting = false
        if access.canRead {
            isImporting = true
        }
    }

    private func finish() {
        Haptics.success()
        onFinish()
    }
}

#Preview {
    OnboardingView(mode: .constant(.business)) {}
        .modelContainer(.preview)
}
