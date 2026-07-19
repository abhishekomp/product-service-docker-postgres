# Multiple GitHub Accounts on One Mac — The SSH Key Problem

> A real problem, a confusing error, and a clean solution.

---

## The Story

You have two GitHub accounts:

| Account | Purpose |
|---------|---------|
| `Abhishek-Omprakash_evinova` | Company account — work projects |
| `abhishekomp` | Personal account — your own projects |

You finish a learning project on your Mac. You set your local git identity to your personal account:

```bash
git config --local user.email "abhishek2283@hotmail.com"
git config --local user.name "abhishekomp"
```

You run `git push`. You expect it to go to your personal GitHub. Instead, you get this:

```
ERROR: Permission to abhishekomp/product-service-docker-postgres.git denied to Abhishek-Omprakash_evinova.
fatal: Could not read from remote repository.

Please make sure you have the correct access rights
and the repository exists.
```

Wait — you literally just configured git with your personal name and email. Why is it still authenticating as your company account?

---

## Why `git config user.name` Did Not Help

This is the key misunderstanding, and it trips up almost everyone who encounters this problem.

**`git config user.name` and `git config user.email` do NOT control authentication.**

They only control the metadata attached to your commits — the name and email that appear in `git log`. They have absolutely nothing to do with which GitHub account is used when you `git push`.

Think of it this way:

```
git config user.name   →  "Sign this commit as Abhishek"       (identity on the commit)
SSH key                →  "Prove to GitHub who you are"         (authentication)
```

GitHub does not look at the commit metadata to decide if you are allowed to push. It looks at the **SSH key** your terminal presents during the connection.

---

## How Git Actually Authenticates to GitHub

When you run `git push`, here is what actually happens:

```
1. Git reads the remote URL:
   git@github.com:abhishekomp/product-service-docker-postgres.git
         ^^^^^^^^^
         This is the SSH host

2. Your terminal opens an SSH connection to github.com

3. SSH looks in ~/.ssh/config for a rule matching "github.com"

4. SSH uses the IdentityFile from that rule to authenticate

5. GitHub checks: "Which account owns this SSH key?"

6. GitHub grants or denies access based on the account that owns the key
```

The crucial step is **step 3**. SSH config controls which key is used — not git config.

---

## The SSH Config — Where the Rules Live

Every SSH connection on your Mac is governed by `~/.ssh/config`. This is a plain text file that maps host names to SSH keys.

Here is what your `~/.ssh/config` looks like:

```
# Default account — Company (Evinova)
Host github.com
  HostName github.com
  IdentityFile /Users/kjss920/.ssh/evinova
  IdentitiesOnly yes
  UseKeychain yes
  User git

# Personal account — abhishek2283
Host github.com-abhishek2283
  HostName github.com
  User git
  IdentityFile ~/.ssh/abhishek2283_id_rsa
  IdentitiesOnly yes

# Another personal account — abhishekomprakash4
Host github.com-abhishekgot
  HostName github.com
  User git
  IdentityFile ~/.ssh/abhishekomprakash4_id_rsa
  IdentitiesOnly yes
```

Read this carefully. There are **three entries**. Each one maps a `Host` label to a real hostname (`HostName github.com`) plus a specific SSH key (`IdentityFile`).

The first entry — the company account — is mapped to the label `github.com`.  
Your personal accounts are mapped to **aliases**: `github.com-abhishek2283` and `github.com-abhishekgot`.

This distinction is everything.

---

## The Problem Visualised

Your project's remote URL was:

```
git@github.com:abhishekomp/product-service-docker-postgres.git
    ^^^^^^^^^^
    SSH connects to host label "github.com"
```

When SSH sees `github.com`, it looks up your `~/.ssh/config` and finds the **first matching rule**:

```
Host github.com          ← matches!
  IdentityFile /Users/kjss920/.ssh/evinova   ← company key used
```

So SSH presents your **company SSH key** to GitHub. GitHub looks up that key, finds it belongs to `Abhishek-Omprakash_evinova`, and then checks: does `Abhishek-Omprakash_evinova` have push access to `abhishekomp/product-service-docker-postgres`? No — that repository belongs to your personal account. **Access denied.**

```
Remote URL          →  SSH host lookup  →  SSH key used       →  GitHub identity
──────────────────────────────────────────────────────────────────────────────────
git@github.com:...  →  "github.com"     →  evinova key        →  company account ❌
```

The fix needs to make this chain end with your personal key instead.

---

## The Solution — Use the SSH Host Alias

The `~/.ssh/config` already has the personal key set up — it is just mapped to the alias `github.com-abhishek2283` instead of the default `github.com`.

The fix is to change the remote URL to reference that **alias** instead of the plain `github.com`:

```bash
git remote set-url origin git@github.com-abhishek2283:abhishekomp/product-service-docker-postgres.git
#                              ^^^^^^^^^^^^^^^^^^^^^^
#                              This alias matches the second entry in ~/.ssh/config
#                              which points to the personal key
```

Now the chain looks like this:

```
Remote URL                        →  SSH host lookup           →  SSH key used              →  GitHub identity
───────────────────────────────────────────────────────────────────────────────────────────────────────────────
git@github.com-abhishek2283:...   →  "github.com-abhishek2283" →  abhishek2283_id_rsa key   →  personal account ✅
```

---

## Verifying It Works

**Step 1 — Confirm the remote URL is updated:**

```bash
git remote -v

# Expected output:
origin  git@github.com-abhishek2283:abhishekomp/product-service-docker-postgres.git (fetch)
origin  git@github.com-abhishek2283:abhishekomp/product-service-docker-postgres.git (push)
```

**Step 2 — Test SSH authentication directly:**

```bash
ssh -T git@github.com-abhishek2283

# Expected output:
Hi abhishekomp! You've successfully authenticated, but GitHub does not provide shell access.
```

This command asks GitHub "who am I when I connect using this host alias?" If it prints your personal username, the right key is being used.

**Step 3 — Push:**

```bash
git push
# Works — no more permission error
```

---

## The Host Alias — How It Actually Works

The alias trick might seem magical at first. Here is exactly what happens:

```
git@github.com-abhishek2283:abhishekomp/repo.git
    ^^^^^^^^^^^^^^^^^^^^^^^^
    SSH treats this as the "host" to connect to.
    It looks this up in ~/.ssh/config.
    
    It finds:
      Host github.com-abhishek2283
        HostName github.com          ← REAL hostname SSH connects to (still github.com)
        IdentityFile ~/.ssh/abhishek2283_id_rsa
    
    So SSH:
    1. Connects to the real host: github.com (port 22)
    2. Presents: abhishek2283_id_rsa  ← the personal key
    3. GitHub authenticates this as the personal account
```

`github.com-abhishek2283` is just a label — a nickname. The actual TCP connection still goes to `github.com`. The alias only exists so SSH knows which key to pick up.

---

## Generalising This — The Pattern for Multiple Accounts

If you ever need to set up a new machine or a new GitHub account from scratch, here is the complete recipe:

**1. Generate a new SSH key for the new account:**
```bash
ssh-keygen -t ed25519 -C "your-personal-email@example.com" -f ~/.ssh/personal_github
```

**2. Add the public key to GitHub:**
```bash
cat ~/.ssh/personal_github.pub
# Copy the output, go to GitHub → Settings → SSH Keys → New SSH Key → paste it
```

**3. Add an alias entry to `~/.ssh/config`:**
```
Host github.com-personal
  HostName github.com
  User git
  IdentityFile ~/.ssh/personal_github
  IdentitiesOnly yes
```

**4. When cloning a personal repo, use the alias:**
```bash
# Instead of:
git clone git@github.com:yourusername/repo.git

# Use the alias:
git clone git@github.com-personal:yourusername/repo.git
```

**5. If you already cloned with the wrong URL, fix the remote:**
```bash
git remote set-url origin git@github.com-personal:yourusername/repo.git
```

---

## Quick Reference — All the Commands Used

```bash
# Check which remote URL is currently configured
git remote -v

# Fix the remote URL to use the personal SSH alias
git remote set-url origin git@github.com-abhishek2283:abhishekomp/product-service-docker-postgres.git

# Verify SSH authentication (which GitHub account does this alias log in as?)
ssh -T git@github.com-abhishek2283

# View your SSH config
cat ~/.ssh/config

# View your local git identity (name/email on commits — NOT authentication)
git config --local user.name
git config --local user.email
```

---

## Summary

| | What it controls |
|--|---|
| `git config user.name` / `user.email` | The name and email shown in `git log` — commit metadata only |
| SSH key in `~/.ssh/config` | Which GitHub account you authenticate as — this is what controls push/pull access |
| Remote URL host label | Which `~/.ssh/config` rule SSH uses to pick the key |

**The one-line fix:**  
Change `git@github.com:` to `git@github.com-abhishek2283:` in the remote URL.  
That is the entire difference between the company key and the personal key being used.

