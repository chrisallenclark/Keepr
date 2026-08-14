import SwiftUI

/// A person's photo, or their initials when there isn't one.
///
/// Initials sit on a neutral fill rather than a color assigned per person —
/// a wall of randomly colored circles is the fastest way to make a contact list
/// look like a toy.
struct Avatar: View {

    enum Size {
        case small
        case medium
        case large

        var diameter: CGFloat {
            switch self {
            case .small: 36
            case .medium: 44
            case .large: 84
            }
        }

        var font: Font {
            switch self {
            case .small: .subheadline
            case .medium: .headline
            case .large: .largeTitle
            }
        }
    }

    let imageData: Data?
    let initials: String
    var size: Size = .medium

    var body: some View {
        Group {
            if let imageData, let image = UIImage(data: imageData) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Circle()
                    .fill(.quaternary)
                    .overlay {
                        Text(initials)
                            .font(size.font)
                            .fontWeight(.medium)
                            .foregroundStyle(.secondary)
                            .minimumScaleFactor(0.6)
                            .padding(size.diameter * 0.2)
                    }
            }
        }
        .frame(width: size.diameter, height: size.diameter)
        .clipShape(.circle)
        .accessibilityHidden(true)
    }
}

extension Avatar {
    init(person: Person, size: Size = .medium) {
        self.init(imageData: person.photoData, initials: person.initials, size: size)
    }

    init(contact: ContactSummary, size: Size = .medium) {
        self.init(imageData: contact.thumbnailData, initials: contact.initials, size: size)
    }
}

#Preview {
    HStack(spacing: 16) {
        Avatar(imageData: nil, initials: "JM", size: .small)
        Avatar(imageData: nil, initials: "SM", size: .medium)
        Avatar(imageData: nil, initials: "MR", size: .large)
    }
    .padding()
}
