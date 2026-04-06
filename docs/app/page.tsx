import Link from "next/link";

export default function HomePage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center text-center px-4">
      <h1 className="text-4xl font-bold mb-4">spec_to_rest</h1>
      <p className="text-fd-muted-foreground text-lg mb-8 max-w-lg">
        Converts a formal behavioral specification written in a custom DSL into
        a complete, verified REST service.
      </p>
      <Link
        href="/docs"
        className="rounded-lg bg-fd-primary px-6 py-3 text-fd-primary-foreground font-medium hover:bg-fd-primary/90 transition-colors"
      >
        Read the docs
      </Link>
    </main>
  );
}
